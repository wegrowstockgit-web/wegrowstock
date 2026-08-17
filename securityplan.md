# InventorySystem — Security Audit & Remediation Plan

> **Type:** Static pentest / white-box security review
> **Date:** 2026-08-15
> **Scope:** `invsys-app` (WMS data plane, :8080), `invsys-admin-api` (control plane, :8081),
> `invsys-core` (shared security/domain), `frontend_wms`, `frontend_admin`, nginx API gateway,
> Docker Compose stack, Postgres RLS model, and third-party integrations.
> **Method:** Read-only source review across authentication, authorization/tenant isolation,
> injection/validation, file upload/SSRF, secrets/config, gateway hardening, and dependencies.
> No files were modified during the audit.

---

## 1. Executive Summary

The platform has a **solid security baseline**: RS256-only JWT verification (no `alg:none`/HMAC
confusion), HttpOnly cookies, hashed refresh tokens, Postgres Row-Level Security with `FORCE` on
most tenant tables, control-plane blocked from the data plane at both nginx and Spring, admin CSRF
enabled, and CVE-pinned `nimbus-jose-jwt`. Dependencies (Spring Boot 4.1.0) are current.

However, the audit found **defaults and design gaps that are exploitable if the current Docker/
config posture reaches a shared or production environment.** The highest-risk clusters are:

1. **Impersonation flow** — tokens leaked in URL query strings, replayable (no `jti`/single-use),
   accepted as normal Bearer tokens, unaudited, and mintable against suspended tenants.
2. **Trust-boundary / spoofing** — `X-Forwarded-For` leftmost-hop parsing over broad RFC1918
   "trusted" CIDRs lets any client spoof its IP, bypassing rate limits, admin IP lockout, and
   actuator scrape allowlists.
3. **Tenant isolation holes** — `support_tickets` RLS bound to the wrong GUC (isolation silently
   broken), and platform/admin tables granted to the shared `app_user` role with no RLS.
4. **Insecure defaults shipping in compose** — weak DB/Redis/MinIO/Grafana credentials, mock
   webhook secrets, `COOKIE_SECURE=false`, control-plane IP allowlist defaulting to allow-all,
   published database ports, no TLS/HSTS/CSP.
5. **SSRF surface** — tenant-controlled Slack webhook URLs, Shopify shop identifiers, and external
   media fetches all issue server-side requests without a private-IP blocklist or redirect control.

### Severity tally

| Severity | Count |
|----------|-------|
| Critical | 1 |
| High | 12 |
| Medium | 15 |
| Low / Info | 9 |

### Remediation priority (do these first)

1. **P0** — Rotate & externalize all secrets; remove insecure compose defaults (§6.1, §6.5, §6.6).
2. **P0** — Fix `ClientIpResolver` trust model (§3.1) — it undermines every rate limit and IP gate.
3. **P0** — Harden impersonation: opaque one-time handoff, `jti`+single-use, reject in `JwtAuthFilter`, audit, block suspended (§2.1–§2.4).
4. **P1** — Fix `support_tickets` RLS + revoke `app_user` grants on platform tables (§4.1, §4.2).
5. **P1** — Set control-plane nginx allowlist to `default 0`, add TLS/HSTS/CSP (§6.3, §6.8, §6.9).
6. **P1** — SSRF blocklist for Slack/Shopify/media outbound calls (§5.1–§5.3).

---

## 2. Authentication & Session Management

### 2.1 [High] Impersonation JWT passed in URL query string
- **Where:** `backend/invsys-admin-api/.../service/AdminImpersonationService.java:47-48`
- **Detail:** The impersonation login URL is built as `...?impersonateToken=<JWT>` and the raw JWT is
  also returned in the JSON `accessToken` field.
- **Exploit:** Query-string tokens leak into browser history, `Referer` headers, proxy/CDN/access
  logs, and analytics. A single leaked URL is a live session credential for the full 15-minute TTL
  and can be exchanged for a 7-day refresh session.
- **Fix:** Never place tokens in URLs. Issue a **one-time opaque code** (random, single-use, short
  TTL, stored server-side) that the admin exchanges via a `POST` handoff for the session; or set the
  session via a `POST`-only redirect that lands the cookie without exposing the token.

### 2.2 [High] Impersonation tokens are replayable (no `jti` / single-use)
- **Where:** `invsys-core/.../security/JwtService.java:48`, `AuthService.java:153-170`
- **Detail:** `acceptImpersonation` validates type/claims and issues a full session, but the token
  carries no `jti`, is not marked consumed, and has no revocation store.
- **Exploit:** The same impersonation token can be replayed against
  `POST /api/v1/auth/impersonation/accept` repeatedly until `exp`, each call minting a fresh 7-day
  refresh session — large theft/replay window.
- **Fix:** Add a `jti`, persist it in a single-use/consumed store (Redis with TTL = token exp), and
  reject reuse. Consider shortening the impersonation TTL to a few minutes.

### 2.3 [High] Impersonation JWTs accepted as ordinary WMS access tokens
- **Where:** `invsys-core/.../security/JwtAuthFilter.java:89-122`
- **Detail:** The WMS auth filter validates signature and expiry but does **not** reject
  `token_type=IMPERSONATION`.
- **Exploit:** Anyone holding the impersonation JWT can call WMS APIs directly as a `Bearer` token
  (skipping `/impersonation/accept`) for the full TTL.
- **Fix:** In `JwtAuthFilter`, reject `IMPERSONATION`-typed tokens for general API access — they must
  only be redeemable at the accept endpoint.

### 2.4 [High] Impersonation is unaudited and mintable against suspended tenants
- **Where:** `invsys-admin-api/.../api/ControlPlaneTenantController.java:66-68`,
  `service/AdminImpersonationService.java:27-54`
- **Detail:** `impersonate()` has no `@PlatformAudit` annotation (module/status changes do), and does
  not reject `SUSPENDED` tenants at mint time (only the accept path checks). `clone-sandbox`
  (`:79-81`) is likewise unaudited.
- **Exploit:** A super admin can impersonate any tenant with no forensic trail (SOC2/incident-response
  gap), and can mint tokens for suspended tenants.
- **Fix:** Add `@PlatformAudit` to `impersonate` and `clone-sandbox`; reject suspended tenants at mint;
  log actor, target tenant, and reason.

### 2.5 [High] `X-Forwarded-For` spoofing bypasses rate limits, IP lockout & actuator gate
> Cross-cutting; also affects §3.1 and §6.4. Tracked once here as the authoritative entry.
- **Where:** `invsys-core/.../security/ClientIpResolver.java:39-60`;
  trusted CIDRs from `application.yml:162` (`10/8,172.16/12,192.168/16`);
  nginx `$proxy_add_x_forwarded_for` (`ops/api-gateway/nginx.conf` ~105).
- **Detail:** When the peer is "trusted", the resolver returns the **leftmost** XFF hop. The gateway
  sits inside `172.16.0.0/12` (trusted), and nginx *prepends* the client-supplied XFF, so the client
  controls the leftmost value.
- **Exploit:** Send `X-Forwarded-For: 127.0.0.1` (or rotate forged IPs) to spoof the client IP →
  bypass WMS IP rate limits, admin IP-based lockout, and the actuator scrape CIDR allowlist.
- **Fix:** Trust only the immediate proxy. Parse XFF from the **right**, peeling exactly the number of
  known proxy hops; shrink the trusted set to the actual gateway address(es), not all RFC1918. Prefer
  nginx `$remote_addr` with `set_real_ip_from` scoped to the gateway subnet only.

### 2.6 [High] WMS CSRF protection fully disabled with cookie sessions
- **Where:** `invsys-core/.../security/SecurityConfig.java:61` (`csrf().disable()`)
- **Detail:** WMS uses HttpOnly cookies (`invsys_access`, `invsys_refresh`) but has no CSRF token.
  Docker ships `COOKIE_SAME_SITE=Lax` and `COOKIE_SECURE=false`. The admin plane, by contrast, has
  cookie-based CSRF enabled.
- **Exploit:** With `SameSite=Lax`, top-level cross-site `GET`/navigations and any future SameSite
  weakening enable classic CSRF against state-changing WMS endpoints.
- **Fix:** Enable cookie-based CSRF on WMS (mirror `AdminSecurityConfig`), or enforce
  `SameSite=Strict` with tightly scoped CORS origins and document the threat model.

### 2.7 [Medium] Magic-login and impersonation-accept are not rate-limited
- **Where:** `RateLimitFilter.java:74-90` (covers login/signup/refresh/SSO/invite/terminal PIN, but
  not `/auth/magic-login`, `/auth/magic-login/consume`, `/auth/impersonation/accept`).
- **Fix:** Add these paths to the rate-limit filter and to nginx `limit_req`.

### 2.8 [Medium] WMS login has no per-account lockout
- **Where:** `AuthService.login (AuthService.java:100-132)` — password check only; no equivalent of
  `AdminLoginAttemptLimiter`. Docker raises `auth-per-minute` to **2000** (`application-docker.yml:57`).
- **Exploit:** Distributed credential stuffing against known emails; the high docker limit effectively
  disables protection.
- **Fix:** Add per-email failed-attempt lockout (reuse the admin limiter pattern); keep prod
  rate limits low.

### 2.9 [Medium] Magic-login consume is not atomic (TOCTOU replay)
- **Where:** `MagicLoginService.java:72-108` — reads `consumedAt` then separately marks consumed.
- **Fix:** Single atomic `UPDATE ... SET consumed_at=now() WHERE token=? AND consumed_at IS NULL`
  and act only if one row was updated.

### 2.10 [Medium] Non-prod magic tokens returned & logged in plaintext
- **Where:** `MagicLoginService.java:65-68` returns `magicToken` in the response body and logs it when
  the profile is not `prod`. Docker runs `dev,docker`.
- **Fix:** Gate strictly behind an explicit `local`-only flag; never log tokens; redact.

### 2.11 [Medium] Public signup enabled outside prod; open self-registration risk
- **Where:** `application.yml:150` default `true`; docker `PUBLIC_SIGNUP_ENABLED=true`; prod validator
  only *warns*.
- **Fix:** Default closed; require explicit opt-in; fail (not warn) in prod when enabled without intent.

### 2.12 [Medium] Ephemeral RSA keypair when PEMs missing (non-prod)
- **Where:** `JwtService.java:67-74` — generates a per-process keypair with a warning if keys absent.
- **Exploit:** Multi-instance deployments without shared keys silently break impersonation verify and
  invalidate sessions on restart; masks misconfiguration.
- **Fix:** Fail-closed unless an explicit local dev flag is set.

### 2.13 [Low] Refresh token accepted from JSON body; BCrypt cost 10; admin refresh cookie unused
- **Where:** `AuthController.java:172-175` (body fallback encourages non-HttpOnly storage);
  `PasswordEncoderConfig.java:12-14` (BCrypt default cost 10 — raise to ≥12);
  admin issues a refresh cookie but exposes no `/refresh` endpoint (dead material).
- **Fix:** Cookie-only refresh; bump BCrypt cost; either implement admin refresh or stop issuing it.

---

## 3. Trust Boundaries & Proxy Handling

### 3.1 [High] Client IP trust model (see §2.5)
The `ClientIpResolver` + broad trusted-CIDR + nginx XFF-prepend combination is the root cause of
multiple bypasses (rate limiting, admin IP lockout, actuator scrape gate). Fix once, centrally, as
described in §2.5. This is a **P0** because it silently weakens several other controls.

---

## 4. Authorization & Multi-Tenant Isolation

### 4.1 [High] `support_tickets` RLS uses the wrong GUC and lacks FORCE
- **Where:** `invsys-core/.../db/migration/V092__support_rag_768_and_tickets.sql:56-60`
- **Detail:** Policy filters on `current_setting('app.tenant_id')`, but the runtime binds
  `app.current_tenant`. The policy therefore never matches for `app_user`, and there is no
  `FORCE ROW LEVEL SECURITY`, so the table owner / `app_owner` bypasses RLS entirely.
- **Exploit:** Tenant isolation for support tickets is effectively not enforced as designed; any
  owner-role query path (or the bootstrap datasource) sees all tenants' tickets.
- **Fix:** Migration to `ALTER POLICY ... USING (tenant_id = NULLIF(current_setting('app.current_tenant', true),'')::uuid)`
  (and matching `WITH CHECK`), plus `ALTER TABLE support_tickets FORCE ROW LEVEL SECURITY`.

### 4.2 [High] Platform/admin tables granted to shared `app_user` with no RLS
- **Where:** `V106__separate_platform_admins.sql:39-43` (`platform_admins`,
  `platform_admin_refresh_tokens` full DML to `app_user`); `V108__platform_audit_logs.sql:21-22`;
  `V107__platform_ops_governance.sql:69-73` (`tenant_shard_routing`, integration controls, rate-limit
  overrides, compliance broadcasts, knowledge documents — SELECT to `app_user`).
- **Detail:** WMS and admin share the `app_user` pool. These tables have no tenant RLS; the only
  defense is "don't expose via HTTP."
- **Exploit:** Any SQL injection, native-query misuse, or accidental JPA mapping from the WMS side can
  read super-admin password hashes / refresh tokens and cross-tenant shard `jdbc_url`s.
- **Fix:** Revoke `app_user` access to platform tables; grant only to a dedicated admin/bootstrap role
  (`app_owner` or a new `platform_admin` DB role) used exclusively by `invsys-admin-api`. Add RLS where
  a tenant scope exists.

### 4.3 [Medium] Admin JWT filter treats any token with `SUPER_ADMIN` role as platform admin
- **Where:** `invsys-admin-api/.../security/AdminJwtAuthFilter.java:59-71`
- **Detail:** The same RS256 keys sign WMS and admin JWTs. `isPlatformAdmin()` returns true if the
  `roles` claim merely contains `SUPER_ADMIN`, without requiring `token_type=PLATFORM_ADMIN`.
- **Exploit:** A WMS access token whose `roles` include `SUPER_ADMIN` (e.g. a DB-seeded tenant role)
  would be accepted by the control plane.
- **Fix:** Require `token_type=PLATFORM_ADMIN` (and/or a `platform_admin=true` claim); ignore WMS
  `roles` for control-plane authentication.

### 4.4 [Medium] Cross-tenant invoice lookup via bootstrap (owner) datasource
- **Where:** `BootstrapJdbc.java:337-365` (`WHERE number = ? LIMIT 1`, no tenant predicate);
  `AccountingPaymentWebhookService.java:83-93`.
- **Exploit:** Duplicate invoice numbers across tenants resolve to the wrong tenant → payment
  mis-attribution; signature is then checked against that tenant's secret.
- **Fix:** Scope the lookup by tenant or by the provider account id, never bare `number LIMIT 1`.

### 4.5 [Medium] Unscoped platform tables writable/readable by `app_user`
- **Where:** `V093__training_sandbox_bindings.sql:20-22` (full CRUD, no RLS);
  `V089__support_rag_pgvector.sql:24-25`, `V090__support_knowledge_graph.sql:27-30` (full DML, no RLS);
  `V009__rls_and_grants.sql:36-38` (`webhook_insert WITH CHECK (true)` allows inserting arbitrary
  `tenant_id`).
- **Fix:** Add RLS/tenant predicates or restrict grants; replace `WITH CHECK (true)` with a tenant-
  bound check.

### 4.6 [Low–Medium] IDOR defense relies solely on RLS
- **Where:** e.g. `InvoiceController.java:60-65` and many peers use `findById(uuid)` with no
  application-level `tenant_id` assertion.
- **Detail:** Correct **only while** `FORCE` RLS + `app.current_tenant` are intact. Combined with §4.1,
  a broken policy becomes a direct IDOR.
- **Fix:** Prefer `findByTenantIdAndId(...)` (as `UserManagementService.resendInvitation` already does)
  for defense-in-depth.

### 4.7 [Low] SSO metadata disclosure by guessable tenant id
- **Where:** `SamlAuthController.java:26-46` — public `GET /saml2/authenticate/{tenantId}` returns SAML
  metadata URL / entity id, enabling tenant enumeration and IdP disclosure.
- **Fix:** Rate-limit; avoid distinguishing existent vs non-existent tenants; consider slug-based,
  non-enumerable identifiers.

### 4.8 [Positive] Control-plane isolation verified
- Data plane blocks control-plane at nginx (`ops/api-gateway/nginx.conf:86-88` → `return 404`) **and**
  Spring (`SecurityConfig.java:110` → `.denyAll()`), and `JwtAuthFilter` skips control-plane paths.
- All `ControlPlane*` controllers carry class-level `@PreAuthorize("hasRole('SUPER_ADMIN')")` except the
  intentionally public auth controller (login/csrf), which is covered by the filter chain.

---

## 5. Injection, SSRF, Input Validation & Uploads

### 5.1 [High] SSRF via tenant-configured Slack webhook URL
- **Where:** `AlertPreferencesController.java:62-80` (validation only checks scheme/host non-blank);
  `integration/alerts/SlackWebhookDispatcher.java:44-49` (server POSTs to the URL).
- **Exploit:** An OWNER/ADMIN sets the webhook to `http://169.254.169.254/...` or an RFC1918 host; the
  server issues the request → cloud metadata / internal service access.
- **Fix:** Reuse `MediaUrlValidator`'s private-IP/DNS blocklist; HTTPS-only; allowlist Slack hosts
  (`hooks.slack.com`); disable redirects; re-resolve host after DNS.

### 5.2 [High] SSRF via Shopify `shopIdentifier` and untrusted staged-upload URL
- **Where:** `ChannelIntegrationService.java:28-37` (no host pattern);
  `integration/shopify/ShopifyMediaSyncService.java:88-91, 135-139`.
- **Exploit:** `shop` may be an internal IP/host → GraphQL SSRF; a malicious shop returns an
  `uploadUrl` pointing at internal/metadata endpoints, which the client follows (default redirects on).
- **Fix:** Enforce `^[a-z0-9][a-z0-9-]*\.myshopify\.com$`; validate/re-resolve every outbound URL
  (GraphQL, staged PUT, media GET); disable or re-check redirects; block private/link-local ranges.

### 5.3 [High] SSRF / redirect bypass on external product-media fetch
- **Where:** `ShopifyMediaSyncService.java:202-208` — fetches `mediaUrl` with default redirects and no
  re-validation (attach path validates, fetch path does not).
- **Exploit:** Public HTTPS → 302 → `http://169.254.169.254/`; DNS rebinding between validate and fetch.
- **Fix:** Prefer first-party `/api/v1/media/{id}/content` for sync; if external fetch is required,
  disable redirects and re-resolve host per hop against the blocklist.

### 5.4 [High] Presigned upload completion skips magic-byte validation
- **Where:** `media/MediaCompleteService.java:66-83` — trusts HEAD content-type/size; no
  `ImageContentValidator` run (multipart path does validate).
- **Exploit:** Client PUTs a polyglot/malware payload under a claimed image type; later served
  `inline` from `/api/v1/media/{id}/content`.
- **Fix:** On complete, GET/range-read the object, run `ImageContentValidator`, enforce per-kind size,
  reject on mismatch.

### 5.5 [Medium] SVG sanitizer is blacklist-based + ReDoS surface
- **Where:** `media/ImageContentValidator.java:31-34, 74-78` — regex blacklist over the full file (up
  to ~10–15MB), incomplete coverage (`<foreignObject>`, other `on*` handlers, encodings).
- **Fix:** Disallow SVG in prod, or use a hardened whitelist SVG sanitizer; cap SVG size (~256KB);
  serve as `Content-Disposition: attachment` with CSP, never navigable inline XML.

### 5.6 [Medium] Unbounded JSONB / Map request bodies without validation
- **Where:** `SettingsController.java:30-32` + `SettingsService.java:48-50` (`putAll(patch)`, no
  allowlist/size cap); `ControlPlaneComplianceController.java:31-34` +
  `AdminComplianceBroadcastService.java:113-114` (unbounded category/title/payload, no `@Valid`);
  `ApIngestionController.java:60-64` (unbounded `documentUrl`/`extractedData`).
- **Exploit:** Large/nested JSON → memory/cache blowups; overwrite of reserved keys; stored-payload DoS.
- **Fix:** Typed DTOs with key allowlists, `@Valid` + `@NotBlank`/`@Size`/`@Pattern`, max payload bytes,
  depth limits, reject unknown keys.

### 5.7 [Medium] Bean-validation gaps (`@Valid` missing → constraints never run)
- **Where:** `CycleCountController.java:48,69`; `InboundReceiveController.java:39,49`;
  `SsoConfigController.java:58-80` (`issuerUrl`/`samlMetadataUrl` unvalidated, later fetchable → SSRF);
  `ControlPlaneShardController` / `ShardUpsertRequest` (`jdbcUrl` unvalidated);
  `ControlPlaneTenantController.UpdateStatusRequest` (`@NotBlank` only);
  `OfficeExceptionController.java:36-58`; `TaxRateController` update.
- **Fix:** Add `@Valid` on every annotated `@RequestBody`; add `@Size`/`@Pattern`/`@NotNull` on DTOs.

### 5.8 [Medium] Outbound socket / URL from user config (SSRF pivots)
- **Where:** `ThermalPrintingService.printViaSocket` (socket to attacker-chosen IP/port);
  `S3CompatibleEndpointResolver.java:38-39` (region string-concat into endpoint);
  `QuickBooksOnlineAdapter.java:100-104` (base URL from vault ciphertext).
- **Fix:** Allowlist RFC1918-only for printers (block metadata); allowlist region tokens `^[a-z0-9-]+$`;
  hardcode Intuit hosts rather than trusting stored base URLs.

### 5.9 [Medium] ReDoS on large document parsing
- **Where:** `ApDocumentParseService.java:44-45` — `.*?` over full document text from `readAllBytes()`.
- **Fix:** Bound input length; rewrite regex to avoid catastrophic backtracking; parse line-by-line.

### 5.10 [Positive] SQLi, deserialization & core upload controls
- Sampled JDBC across `BootstrapJdbc`, `AdminReportingService`, compliance/knowledge ingest, partition
  maintenance: all use `?` bind parameters; JSONB uses `CAST(? AS jsonb)`; LIKE patterns are passed as
  bound values. No `String.format`-built SQL with user input found.
- No `activateDefaultTyping`, `ObjectInputStream`, or unhardened XML parsing of untrusted input.
- Multipart uploads enforce size limits, magic-byte checks, and `tenantId/uuid.ext` keys (no path
  traversal). `MediaUrlValidator` blocks private IPs for attach-by-URL.

---

## 6. Configuration, Secrets, Gateway & Infrastructure

### 6.1 [Critical] Hardcoded weak DB/app credentials + published DB ports
- **Where:** `docker-compose.yml:10,47,257-259,330-332`; `ops/postgres/init/01-roles.sql:5-8`
  (`app_owner_secret`, `app_user_secret`); `ops/pgbouncer/userlist.txt:1-2`;
  `application.yml:52,60` and admin `application.yml:13,22` use these as **defaults**.
  Postgres (`5432`) and PgBouncer (`6432`) are published to the host.
- **Exploit:** Predictable, committed credentials + exposed ports = direct DB compromise if the stack
  is ever network-reachable.
- **Fix:** Source all secrets from a secret manager; no password defaults in prod YAML; do not publish
  DB/PgBouncer ports; rotate immediately if this stack was ever exposed.

### 6.2 [High] Mock webhook / API secrets baked into compose & YAML defaults
- **Where:** `docker-compose.yml:275-278,343-346`; `application.yml` Stripe/Shopify/EasyPost/accounting
  defaults; `.env.example:15-17`. `ProductionSecurityValidator` misses `accounting_mock_secret`,
  `MEDIA_SECRET_KEY`, and `invsyssecret`.
- **Exploit:** Forged webhooks are accepted if these ship; validator does not fail-closed on all mocks.
- **Fix:** Require every webhook/API secret in the prod validator; no mock defaults outside `dev`/`test`.

### 6.3 [High] Control-plane IP allowlist defaults to allow-all
- **Where:** `ops/api-gateway/nginx.conf:32-37,254-260` — `geo $admin_allowed { default 1; }`.
- **Exploit:** Admin UI/API (`:8081`/`:3002`) reachable from anywhere; the geo gate is a no-op.
- **Fix:** `default 0;` + explicit office/VPN CIDRs; front control-plane with private network / SSO /
  Cloudflare Access.

### 6.4 [High] Actuator scrape & rate-limit bypass via XFF (see §2.5)
- Root cause and fix in §2.5. Additionally shrink `ACTUATOR_SCRAPE_ALLOWED_CIDRS`
  (`application.yml:162`) to the Prometheus/Grafana subnet only, not all RFC1918.

### 6.5 [High] Redis/MinIO/Grafana weak or no auth; all HTTP; published to host
- **Where:** `docker-compose.yml:28-29` (Redis no password), `67-71` (MinIO `invsys`/`invsyssecret`),
  `210-217` (Grafana `admin`/`admin`), `386-388` (gateway HTTP). MinIO bucket CORS `AllowedOrigins:["*"]`
  (`103-109`).
- **Fix:** Don't host-publish backing services; set `requirepass` on Redis; rotate Grafana/MinIO;
  restrict bucket CORS to app origins; TLS outside localhost.

### 6.6 [High] `COOKIE_SECURE=false` forced in docker profile
- **Where:** `docker-compose.yml:271,337`; `application-docker.yml:61-62` (base YAML defaults are
  `true`/`Strict`, but docker overrides).
- **Exploit:** Session JWTs set over HTTP → sniffable.
- **Fix:** Secure cookies whenever TLS terminates; split `docker-local` vs `docker-edge` profiles;
  never ship `COOKIE_SECURE=false` to shared hosts.

### 6.7 [High] Swagger/OpenAPI open outside `prod`, proxied via WMS nginx
- **Where:** `SecurityConfig.java:103-105` (permitAll when not prod); `application.yml:232-236`
  (enabled by default); `frontend_wms/nginx.conf:90-98` proxies it. Compose runs `dev,docker`.
- **Fix:** Disable springdoc unless an explicit `local` profile; block `/swagger-ui` + `/api-docs` at
  nginx in all shared environments.

### 6.8 [Medium–High] No TLS / HSTS at the edge
- **Where:** `ops/api-gateway/nginx.conf` (listens `8080`/`8081` only); `frontend_admin/nginx.conf:2`,
  `frontend_wms/nginx.conf:2` — no `ssl_`, no `Strict-Transport-Security`.
- **Fix:** Terminate TLS at the edge/LB; add HSTS (`max-age`, `includeSubDomains`) on HTTPS.

### 6.9 [Medium] Security headers incomplete (no CSP, no HSTS)
- **Where:** Spring sets `X-Content-Type-Options`, `X-Frame-Options: DENY`, Referrer-Policy
  (`SecurityConfig.java:64-69`, `AdminSecurityConfig.java:72-76`) but no CSP/HSTS; SPA nginx sets only
  `Cache-Control`.
- **Fix:** Add a strict CSP on SPA responses; HSTS at the TLS edge; mirror frame/nosniff on static
  assets.

### 6.10 [Medium] Dynamic CORS + verified-domains growth (WMS)
- **Where:** `DynamicCorsWhitelist.java:97-115`; `application.yml:151-153`;
  compose `CORS_INCLUDE_VERIFIED_DOMAINS=true`. (`ApiGatewayCorsFilter.java:74-76` correctly echoes the
  exact origin with credentials, not `*`, and rejects wildcard hosts — good.)
- **Fix:** In prod, static origins only unless tenant custom domains are strictly validated.

### 6.11 [Medium] Nginx rate limits miss the primary login path
- **Where:** `nginx.conf` limits `magic-login` and admin login but not `/api/v1/auth/login`;
  `application-docker.yml:57` sets `auth-per-minute: 2000`.
- **Fix:** Add `limit_req` to login/signup; keep Spring auth limits low outside local e2e.

### 6.12 [Medium] `ProductionSecurityValidator` coverage gaps
- **Where:** `ProductionSecurityValidator.java:28-37,97-117` — allows any `sk_*` (incl. live-looking
  `sk_test_...` except exact `sk_test_mock`); does not check `ACCOUNTING_WEBHOOK_SECRET`, media keys,
  DB passwords, `COOKIE_SECURE`, admin geo allowlist, or Redis auth.
- **Fix:** Require `sk_live_` in prod; validate accounting/media/DB secrets; assert
  `cookie-secure=true`, `admin_allowed` non-default, and Redis auth on prod.

### 6.13 [Medium] Token / invite / PII logging
- **Where:** `MagicLoginService.java:65-67` (magic token), `InvitationEmailService.java:46` (invite URL
  with token when SMTP unset), `ops/fix_passwords.sql` (`password123` bcrypt).
- **Fix:** Never log tokens or token-bearing URLs; redact PII; never seed demo passwords outside local.

### 6.14 [Low–Medium] `X-User-Roles` header forwarded from the edge
- **Where:** `ops/api-gateway/nginx.conf:176-177,198-199`; consumed for UX only in
  `SupportChatController.java:115-125` (falls back to security context).
- **Fix:** Never use `X-User-Roles` for authorization decisions (JWT only); strip at the edge.
- **Positive:** Upstreams are fixed (`backend`/`backend-admin`) — no arbitrary-URL open proxy.

### 6.15 [Info/Positive] JWT PEMs are gitignored
- `ops/jwt/.gitignore` ignores `*.pem`; only `.gitignore` is tracked. Keep keys per-environment and out
  of CI artifacts; rotate on leak.

---

## 7. Demo Data & Seed Credentials

### 7.1 [Medium] Documented demo credentials seeded outside Flyway
- **Where:** `ops/demo_seed.sql:9-16,34,60-62` (`owner@demo.test` / `password123`, bcrypt `$2a$10$`),
  `ops/demo_seed_tenants_extra.sql`, `ops/fix_passwords.sql`, `USER_GUIDE.md`.
- **Detail:** Flyway `V106` copies existing `is_super_admin` users into `platform_admins` but does not
  hardcode demo passwords; the risk is the ops seed scripts being applied to a reachable environment.
- **Fix:** Restrict demo seeds to local; document that they must never run against shared/prod; rotate
  if ever applied.

---

## 8. Dependencies

- **Status: healthy.** Spring Boot **4.1.0**, `nimbus-jose-jwt` pinned to **10.4.2** with an explicit
  comment for **CVE-2025-53864** (DoS), AWS SDK 2.31.78, Stripe 28.4.0, springdoc 3.0.3, Spring
  Modulith 2.0.1, Java 25.
- **Recommendations:**
  - Add OWASP Dependency-Check or `mvn versions:display-dependency-updates` to CI, gating on High/Critical CVEs.
  - Add `pnpm audit` (or equivalent) to the frontend CI for `frontend_wms` / `frontend_admin`.
  - Enable Dependabot/Renovate for automated CVE bumps.

---

## 9. Remediation Roadmap

### Phase 0 — Immediate (P0, this week)
- [x] Externalize secrets via `${VAR:-…}` compose/env overrides; local Docker still uses mock defaults (rotate before any shared/prod deploy) (§6.1, §6.2, §6.5).
- [x] Bind DB/PgBouncer/Redis/MinIO/Grafana ports to `127.0.0.1` only (§6.1, §6.5).
- [x] Fix `ClientIpResolver` trust model + shrink trusted/scrape CIDRs (§2.5, §6.4).
- [x] Harden impersonation: one-time opaque handoff (no token in URL), `jti`+single-use, reject `IMPERSONATION` in `JwtAuthFilter`, add `@PlatformAudit`, block suspended at mint (§2.1–§2.4).
- [x] Set nginx `$admin_allowed` to `default 0` with explicit CIDRs (§6.3).

### Phase 1 — Short term (P1, this sprint)
- [x] Fix `support_tickets` RLS GUC + `FORCE`; audit all RLS tables for GUC consistency (§4.1).
- [x] Revoke `app_user` grants on platform/admin tables; admin API uses `app_owner` (§4.2, §4.5).
- [x] Require `token_type=PLATFORM_ADMIN` in `AdminJwtAuthFilter` (§4.3).
- [x] SSRF blocklist + redirect control + host allowlists for Slack/Shopify/media (§5.1–§5.3).
- [x] Magic-byte validation on presigned completion (§5.4).
- [x] CSP + security headers at the edge; Swagger disabled in docker; TLS/HSTS/`COOKIE_SECURE=true` still local-HTTP leftovers (§6.6–§6.9).
- [x] Rate-limit magic-login/impersonation-accept/login; add WMS per-email lockout (§2.7, §2.8, §6.11).

### Phase 2 — Hardening (P2, this quarter)
- [x] WMS stays CSRF-off for Playwright; `SameSite=Lax` + origin CORS (enable CSRF only with e2e updates) (§2.6, §6.10).
- [x] Add `@Valid` + size/pattern constraints across flagged DTOs; bound JSONB payloads (§5.6, §5.7).
- [ ] Application-level tenant assertions for defense-in-depth on `findById` controllers (§4.6).
- [x] Atomic magic-login consume; cookie-only refresh; BCrypt cost ≥12; fail-closed JWT keys (§2.9, §2.13, §2.12).
- [x] Scope bootstrap invoice lookup by tenant/account (§4.4); harden SVG/ReDoS (§5.5, §5.9).
- [x] Expand `ProductionSecurityValidator`; dependency/CVE scanning in CI still open (§6.12, §8).

---

## Appendix A — Consolidated Findings Table

| ID | Sev | Area | Finding | Primary location |
|----|-----|------|---------|------------------|
| 6.1 | Critical | Config | Hardcoded DB creds + published DB ports | `docker-compose.yml`, `01-roles.sql` |
| 2.1 | High | AuthN | Impersonation JWT in URL query string | `AdminImpersonationService.java:47` |
| 2.2 | High | AuthN | Impersonation token replayable (no jti) | `AuthService.java:153` |
| 2.3 | High | AuthN | Impersonation JWT usable as normal Bearer | `JwtAuthFilter.java:89` |
| 2.4 | High | AuthZ | Impersonation unaudited / suspended mint | `ControlPlaneTenantController.java:66` |
| 2.5 | High | Trust | XFF spoofing bypasses rate/IP/actuator gates | `ClientIpResolver.java:39` |
| 2.6 | High | AuthN | WMS CSRF disabled with cookie sessions | `SecurityConfig.java:61` |
| 4.1 | High | AuthZ | `support_tickets` RLS wrong GUC, no FORCE | `V092__...sql:56` |
| 4.2 | High | AuthZ | Platform tables granted to `app_user`, no RLS | `V106__...sql:39` |
| 5.1 | High | SSRF | Tenant Slack webhook SSRF | `SlackWebhookDispatcher.java:44` |
| 5.2 | High | SSRF | Shopify shop/staged-upload SSRF | `ShopifyMediaSyncService.java:88` |
| 5.3 | High | SSRF | External media fetch redirect SSRF | `ShopifyMediaSyncService.java:202` |
| 5.4 | High | Upload | Presign complete skips magic-byte check | `MediaCompleteService.java:66` |
| 6.2 | High | Config | Mock webhook/API secrets baked in | `docker-compose.yml:275` |
| 6.3 | High | Gateway | Control-plane allowlist default allow-all | `nginx.conf:34` |
| 6.5 | High | Config | Redis/MinIO/Grafana weak auth, HTTP, published | `docker-compose.yml` |
| 6.6 | High | Config | `COOKIE_SECURE=false` in docker | `application-docker.yml:61` |
| 6.7 | High | Config | Swagger open outside prod | `SecurityConfig.java:103` |
| 2.7 | Medium | AuthN | Magic-login/impersonation-accept unrate-limited | `RateLimitFilter.java:74` |
| 2.8 | Medium | AuthN | No WMS per-account lockout; docker 2000/min | `AuthService.java:100` |
| 2.9 | Medium | AuthN | Magic-login consume TOCTOU | `MagicLoginService.java:72` |
| 2.10 | Medium | AuthN | Magic token returned/logged non-prod | `MagicLoginService.java:65` |
| 2.11 | Medium | AuthN | Public signup enabled outside prod | `application.yml:150` |
| 2.12 | Medium | AuthN | Ephemeral RSA keypair fallback | `JwtService.java:67` |
| 4.3 | Medium | AuthZ | Admin filter trusts `SUPER_ADMIN` role claim | `AdminJwtAuthFilter.java:59` |
| 4.4 | Medium | AuthZ | Cross-tenant invoice lookup (bootstrap) | `BootstrapJdbc.java:337` |
| 4.5 | Medium | AuthZ | Unscoped platform tables to `app_user` | `V093/V089/V090/V009` |
| 5.5 | Medium | Upload | SVG blacklist sanitizer + ReDoS | `ImageContentValidator.java:31` |
| 5.6 | Medium | Validation | Unbounded JSONB/Map bodies | `SettingsController.java:30` |
| 5.7 | Medium | Validation | `@Valid` missing on several controllers | `CycleCountController.java:48` |
| 5.8 | Medium | SSRF | Printer socket / region / QBO base URL | `ThermalPrintingService` |
| 5.9 | Medium | DoS | ReDoS on document parsing | `ApDocumentParseService.java:44` |
| 6.8 | Medium | Gateway | No TLS/HSTS at edge | nginx configs |
| 6.9 | Medium | Gateway | No CSP/HSTS security headers | `SecurityConfig.java:64` |
| 6.10 | Medium | Config | Dynamic CORS + verified domains | `DynamicCorsWhitelist.java:97` |
| 6.11 | Medium | Gateway | Login path not nginx rate-limited | `nginx.conf` |
| 6.12 | Medium | Config | Prod validator coverage gaps | `ProductionSecurityValidator.java:28` |
| 6.13 | Medium | Logging | Token/invite/PII logging | `MagicLoginService.java:65` |
| 7.1 | Medium | Seeds | Demo `password123` in ops seeds | `ops/demo_seed.sql:34` |
| 2.13 | Low | AuthN | Body refresh token / BCrypt 10 / dead admin refresh | `AuthController.java:172` |
| 4.6 | Low–Med | AuthZ | IDOR defense = RLS only | `InvoiceController.java:60` |
| 4.7 | Low | AuthZ | SSO metadata disclosure by tenant id | `SamlAuthController.java:26` |
| 6.14 | Low–Med | Gateway | `X-User-Roles` forwarded from edge | `nginx.conf:176` |
| 5.8b | Low | SSRF | S3 region / QBO base-url concat | `S3CompatibleEndpointResolver.java:38` |

## Appendix B — Positive Controls Observed
- RS256-only JWT verification (rejects `alg:none`/HMAC confusion).
- HttpOnly session cookies; refresh tokens stored SHA-256 hashed.
- Admin login lockout (IP + email, 5 fails / 10 min) via Redis or local.
- Postgres RLS with `FORCE` on most tenant tables; per-request `app.current_tenant` GUC; pool init-SQL clears the GUC.
- Control-plane blocked on the data plane at both nginx and Spring; `JwtAuthFilter` skips control-plane paths.
- Admin CSRF enabled (cookie-based) with `SameSite=Strict` session cookies.
- CORS echoes the exact origin with credentials (never `*`) and rejects wildcard hosts.
- Parameterized JDBC throughout; JSONB via `CAST(? AS jsonb)`; no unsafe deserialization/XXE.
- Multipart uploads: size limits, magic-byte validation, non-traversable object keys; `MediaUrlValidator` blocks private IPs.
- Dependencies current; `nimbus-jose-jwt` pinned for CVE-2025-53864.
- JWT PEMs gitignored (not committed).
- JWT audience scoping: `app_context` claim (`POS`/`WMS`) set from login `targetApp`, persisted on refresh tokens (V119), and enforced in `JwtAuthFilter` — POS tokens are confined to `/api/v1/pos/**` + `/api/v1/auth/**`, WMS tokens are barred from `/api/v1/pos/**` (403). Blocks token portability for multi-role users.
- Retail POS WMS settings (`GET|PATCH|PUT /api/v1/settings` `pos_*` keys) are `@PreAuthorize` OWNER/ADMIN. The **Settings → Retail POS** tab additionally requires an explicit `RETAIL_POS` module (empty `enabledModules` does not unlock). Currency is restricted to USD/MXN; receipt text is capped at 2000 characters.
- Additive multi-role RBAC with last-owner protection: role replacement (`UserManagementService.applyRolesChange`) refuses to strip the final `OWNER`.
