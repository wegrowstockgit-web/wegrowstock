# Security Plan & Penetration Test Report — InventorySystem (WeGrowStock)

**Report type:** White-box penetration test / security audit
**Scope:** Entire project — `backend/` (Spring Boot 4.1.0, Java 25), `frontends/` (React 19 / Vite), `ops/` (Docker Compose, nginx, Terraform), configuration and secrets.
**Date:** 2026-08-18
**Method:** Source-driven review (SAST-style) with manual exploit reasoning. Findings below were confirmed by reading the referenced source.

> **How to read this document.** Each finding has: **Where** (file + lines), **What** (the flaw), **Exploit** (concrete attacker steps), **Impact**, and **Fix**. Severity uses CVSS-style bands: Critical / High / Medium / Low / Info. A prioritized remediation roadmap is at the end.

---

## Executive summary

The application has a genuinely strong security *foundation*: JWT is RS256-only (rejects `alg:none`/HMAC confusion), passwords use BCrypt cost 12, PostgreSQL Row-Level Security with `FORCE` is applied to core tenant tables, a `ProductionSecurityValidator` fails startup on mock secrets/insecure cookies under the `prod` profile, and CORS is exact-origin (no `*` with credentials). JWT PEMs and `.env` are gitignored.

However, the pentest surfaced **several exploitable issues**, the most serious being logic/authorization flaws rather than framework misuse:

| # | Severity | Finding |
|---|----------|---------|
| C-1 | **Critical** | Invitation-accept endpoint serializes the full `User` entity, leaking BCrypt password hash + terminal PIN hash to an unauthenticated caller |
| C-2 | **Critical** | Accounting webhook treats the **raw shared secret as a valid signature** (auth bypass / webhook forgery) |
| C-3 | **Critical** | Accounting webhook looks up invoices **before** signature verification → cross-tenant invoice-existence oracle |
| H-1 | High | Thermal `DIRECT_SOCKET` printer → server-side TCP connect to any attacker-supplied IP (SSRF / internal pivot) |
| H-2 | High | QuickBooks `baseUrl` taken from vault, used in `HttpClient` with no host allowlist (SSRF + bearer-token exfiltration) |
| H-3 | High | Mock webhook/media secrets shipped as config defaults; `docker`/`default` profiles are **not** covered by the prod validator |
| H-4 | High | `expose-magic-token=true` + open signup on docker/dev; magic-token exposure is **not** prod-gated |
| H-5 | High | Docker Compose default creds (Postgres/MinIO/Grafana), Redis with no AUTH, committed PgBouncer `userlist.txt`; WMS/POS/API bound to `0.0.0.0` |
| H-6 | High | No HSTS at TLS edge (nginx / SPA) |
| M-1..M-9 | Medium | CSRF disabled with cookie auth, non-atomic OAuth-state consume (TOCTOU), no refresh-token reuse detection, stored-XSS via inline SVG, DNS-rebinding on outbound fetch, credentialed CORS from self-service verified domains, SSO paths not rate-limited, invite token logged/returned, empty prod control-plane CIDR allowlist |
| L/Info | Low/Info | PIN keyspace, timing-leak in Shopify HMAC length check, SPA CSP `unsafe-inline`, demo password SQL committed, SAML stub |

**Top priority:** fix C-1, C-2, C-3 immediately — they are directly exploitable over the network and lead to credential disclosure, auth bypass, and cross-tenant data inference.

---

## Methodology

1. **Attack-surface mapping** — enumerated controllers (public vs. authenticated), webhook endpoints, OAuth/SSO flows, file upload/media, integration adapters that make outbound calls, and infra (Docker, nginx, Terraform).
2. **Auth & session review** — JWT signing/verification, cookie flags, refresh rotation, tenant isolation (RLS + `TenantContext`).
3. **Injection & data-exposure review** — SQL/command injection, SSRF, path traversal, deserialization, XSS, secret handling, webhook signature verification (timing-safety).
4. **Infra & config review** — CORS, security headers, container defaults, actuator exposure, dependency versions, rate limiting, TLS/cookie profiles.
5. **Manual confirmation** — read the source for each high/critical finding to eliminate false positives.

---

## Critical findings

### C-1 — Password & PIN hashes leaked to unauthenticated invitation-accept caller

**Where:**
- `backend/invsys-core/src/main/java/com/invsys/api/InvitationController.java:22-25`
- `backend/invsys-core/src/main/java/com/invsys/domain/User.java:17-18, 26-27, 92-98, 116-122`

```22:25:backend/invsys-core/src/main/java/com/invsys/api/InvitationController.java
    @PostMapping("/accept")
    public User accept(@Valid @RequestBody AcceptInvitationRequest request) {
        return userManagementService.acceptInvitation(request.token(), request.displayName(), request.password());
    }
```

The controller returns the JPA `User` entity directly. `User` exposes `getPasswordHash()` and `getTerminalPinHash()` as public getters with **no `@JsonIgnore`**, so Jackson serializes them. `/api/v1/invitations/**` is not behind `@PreAuthorize` and the accept flow is reachable by anyone holding an invite token.

**Exploit:**
```
POST /api/v1/invitations/accept
{ "token": "<invite-token-from-email>", "displayName": "x", "password": "Passw0rd!" }
```
Response body includes `"passwordHash":"$2a$12$..."` (and `terminalPinHash` once set). An attacker who intercepts or completes an invite obtains the BCrypt hash for **offline cracking** and the terminal PIN hash (only a 4-digit space — see L-1) which is trivially reversible offline.

**Impact:** Credential disclosure → account takeover; PIN hash is offline-crackable in milliseconds.

**Fix:**
- Return a dedicated response DTO (e.g. `UserProfileResponse`) that contains only non-secret fields. Never serialize the `User` entity to a client.
- Defense in depth: annotate `passwordHash`/`terminalPinHash` getters (or fields) with `@JsonIgnore`, or add `@JsonProperty(access = WRITE_ONLY)`.
- Audit every controller that returns `User` (e.g. `UserController`, auth/me endpoints) for the same leak.

---

### C-2 — Accounting webhook accepts the raw shared secret as a valid signature

**Where:** `backend/invsys-core/src/main/java/com/invsys/service/AccountingPaymentWebhookService.java:137-158`

```141:144:backend/invsys-core/src/main/java/com/invsys/service/AccountingPaymentWebhookService.java
        String provided = signatureHeader.trim();
        if (provided.equals(secret)) {
            return true;
        }
```

The signature verifier returns `true` if the caller simply sends the **shared secret itself** in the `X-Accounting-Signature` header, bypassing HMAC entirely. Combined with H-3 (mock secret defaults), on any non-`prod` deployment the secret is the well-known `accounting_mock_secret`.

**Exploit:**
```
POST /api/v1/public/webhooks/accounting/xero
X-Accounting-Signature: accounting_mock_secret
{ "invoiceNumber": "INV-1001", "status": "PAID" }
```
The webhook marks the invoice `PAID` and emits an `INVOICE_PAID` outbox event — no HMAC computed. Even with a real secret, anyone who ever sees the plaintext secret (logs, a header echo, a config leak) can forge webhooks forever.

**Impact:** Financial fraud — invoices marked paid without payment; downstream fulfillment/accounting corruption.

**Fix:** Delete the `provided.equals(secret)` branch. Require HMAC-SHA256 only, compared with `MessageDigest.isEqual` (already implemented below the branch). Fail closed when the secret is blank or equals a known mock value outside test.

---

### C-3 — Invoice lookup before signature verification (cross-tenant existence oracle)

**Where:** `backend/invsys-core/src/main/java/com/invsys/service/AccountingPaymentWebhookService.java:77-92`

```83:91:backend/invsys-core/src/main/java/com/invsys/service/AccountingPaymentWebhookService.java
        var lookup = bootstrapJdbc.findInvoiceByNumberOrId(invoiceKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));

        TenantContext.setTenantId(lookup.tenantId());
        try {
            String secret = resolveWebhookSecret(lookup.tenantId(), normalized);
            if (!isValidSignature(rawBody, signatureHeader, secret)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", ...);
```

The public endpoint resolves an invoice by number/ID **across all tenants** (`bootstrapJdbc` bypasses RLS) *before* validating the signature. The response differs: `404` when the invoice does not exist, `401` when it exists but the signature is wrong.

**Exploit:** With no valid signature, iterate invoice numbers (`INV-1000`, `INV-1001`, …) against `/api/v1/public/webhooks/accounting/xero`; `401` reveals a real invoice, `404` reveals a gap — a cross-tenant enumeration oracle over the `invoices` table.

**Impact:** Information disclosure — reveals existence, volume, and numbering scheme of other tenants' invoices.

**Fix:** Verify the signature **before** any DB lookup. Resolve the secret from a tenant/provider identifier carried in the path or HMAC key derivation, not from the invoice. Return an identical response (e.g. `202 Accepted` or generic `400`) for both bad-signature and unknown-invoice cases.

---

## High findings

### H-1 — Thermal `DIRECT_SOCKET` printer enables SSRF to arbitrary hosts

**Where:** `backend/invsys-core/src/main/java/com/invsys/service/ThermalPrintingService.java:145-162, 171-194`

```151:152:backend/invsys-core/src/main/java/com/invsys/service/ThermalPrintingService.java
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printer.getIpAddress().trim(), port), 10_000);
```

`validatePrinterConfig` checks only that an IP is present and the port is 1–65535 — **no private/link-local/metadata blocklist**. An authenticated user with printer-config rights creates a `DIRECT_SOCKET` printer pointing at any address; printing opens a TCP connection from inside the app network.

**Exploit:** Create a printer with `ipAddress=169.254.169.254` (cloud metadata) or `10.x.x.x:6379` (internal Redis/other service), then trigger a print. The server connects and writes attacker-controlled ZPL bytes to the internal service.

**Impact:** SSRF / internal network pivot / port-scan; potential interaction with internal services.

**Fix:** Reuse the existing `MediaUrlValidator.isBlockedAddress` logic to reject private/loopback/link-local/metadata ranges; allowlist corporate printer CIDRs; prefer PrintNode (cloud) over raw sockets. Enforce at both create and print time (resolve DNS once and pin).

---

### H-2 — QuickBooks `baseUrl` from vault used unvalidated in outbound HTTP

**Where:** `backend/invsys-core/src/main/java/com/invsys/integration/accounting/QuickBooksOnlineAdapter.java:159-163, 217-232`

`baseUrl` is decoded from the credential ciphertext (`parts[2]`) and concatenated into the request URI with the OAuth bearer token attached — no pinning to Intuit hosts.

**Exploit:** Anyone able to write a poisoned credential (or a compromised vault entry) sets `baseUrl=http://169.254.169.254` (or an internal admin API); the server then POSTs the bearer token there → SSRF + token exfiltration.

**Impact:** SSRF, OAuth token leakage.

**Fix:** Hardcode Intuit sandbox/prod base URLs (select by environment, not by stored credential). Validate `realmId` as digits only. Apply the same private-IP allowlist as H-1.

---

### H-3 — Mock secrets as configuration defaults; `docker`/`default` profiles not prod-validated

**Where:**
- `backend/invsys-core/src/main/resources/application.yml:130-156, 227` (`whsec_mock_secret`, `shopify_mock_secret`, `easypost_mock_secret`, `accounting_mock_secret`, `MEDIA_SECRET_KEY` default `invsyssecret`)
- `backend/invsys-admin-api/src/main/resources/application.yml:55-66`
- `docker-compose.yml:~298-301`

`ProductionSecurityValidator` only rejects mocks under `prod`/`production`. A stack launched with `docker` or an unset (`default`) profile keeps the mock secrets.

**Exploit:** Any internet-reachable non-prod deployment lets an attacker forge Stripe/Shopify/EasyPost/accounting webhooks and abuse media signing, because the secrets are public in the repo.

**Impact:** Webhook forgery, signed-URL forgery.

**Fix:** No secret defaults in any deployable config; require env vars and fail startup when blank. Keep mocks only in `application-test.yml`. Treat `docker` as prod for secret validation if it can ever be exposed.

---

### H-4 — Magic-token exposure and open signup enabled outside prod, not prod-gated

**Where:**
- `backend/invsys-core/src/main/java/com/invsys/core/security/MagicLoginService.java:67-69`
- `application-docker.yml` / `application-dev.yml` (`expose-magic-token: true`, `public-signup-enabled: true`)
- `docker-compose.yml:302-303`
- `ProductionSecurityValidator.java:151-156` (validates signup + cookies, **not** `expose-magic-token`)

**Exploit:** On a docker/dev stack, `POST` the magic-login request and read `magicToken` straight from the JSON response → full session without email access. Open signup lets anyone provision tenants.

**Impact:** Authentication bypass / account takeover on any exposed non-prod environment.

**Fix:** Default `expose-magic-token=false` everywhere except local test; add a fail-fast check for it to `ProductionSecurityValidator`; gate the compose overrides behind an explicit `demo` profile.

---

### H-5 — Insecure Docker Compose defaults and host exposure

**Where:** `docker-compose.yml` (Postgres `postgres/postgres` :8/12; MinIO `invsys/invsyssecret`; Grafana `admin/admin`; Redis no AUTH; WMS `3000`, POS `3003`, gateway `8080` on `0.0.0.0`), `ops/pgbouncer/userlist.txt` (committed plaintext role passwords).

**Exploit:** On shared networks, LAN peers reach the exposed UI/API (which also ship open signup + magic tokens, H-4). A local process connects to Postgres/Redis/MinIO with default creds and dumps all tenant data. Repo clone reveals PgBouncer role passwords.

**Impact:** Full data compromise if the compose stack is used beyond an isolated laptop.

**Fix:** Bind data services and non-admin UIs to `127.0.0.1`; require env-provided credentials with no weak defaults; set Redis `requirepass`; move `userlist.txt` secrets out of git and rotate them; document this compose as local-only.

---

### H-6 — No HSTS at the TLS edge

**Where:** `ops/api-gateway/nginx.conf:74-77`, `frontends/apps/*/nginx.conf` (set XCTO/XFO/Referrer/CSP but not `Strict-Transport-Security`); `SecurityConfig.java:63-70` (Spring sets CSP/XFO/Referrer/Permissions-Policy, not HSTS — acceptable since TLS terminates at the edge).

**Exploit:** SSL-stripping / first-request-over-HTTP downgrade on real domains; cookies with `Secure` are never sent over HTTP but the session can still be MITM'd on the initial navigation.

**Impact:** Transport downgrade on production domains.

**Fix:** Add `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload` on TLS-terminating nginx only. Add `Permissions-Policy` to the SPA nginx configs.

---

## Medium findings

### M-1 — CSRF disabled on the data plane with cookie-based auth
`SecurityConfig.java:60` disables CSRF while sessions ride HttpOnly cookies. Mitigated by `SameSite=Strict` in prod, but **broken** under docker/dev (`Lax`, `Secure=false`). **Fix:** keep `Strict`+`Secure` in all non-local envs; add a double-submit CSRF token for state-changing APIs if `SameSite=None` is ever needed for cross-subdomain. (Admin plane already uses CSRF correctly — `AdminSecurityConfig.java:50-70`.)

### M-2 — OAuth state consume is non-atomic (TOCTOU)
`BootstrapJdbc.java:360-383` does `SELECT` then a separate `DELETE`; expiry is checked in the controller after consume (`PublicOauthCallbackController.java:66-71`). Two concurrent callbacks with the same `state` can both pass. **Fix:** single atomic `DELETE ... WHERE state=? AND expires_at > now() RETURNING ...`.

### M-3 — Refresh rotation without reuse detection
`AuthService.java:312-357` rotates refresh tokens but a replayed (already-revoked) token only returns `INVALID_TOKEN` — no token-family revocation. **Fix:** on reuse of a revoked refresh token, revoke the entire family/user session chain (via a `replaced_by` lineage).

### M-4 — Stored XSS via inline-served SVG
`MediaController.java:108-120` serves uploaded content `Content-Disposition: inline` with the stored content type; `ImageContentValidator.java:32-34, 79-84` uses a regex blocklist only. A crafted SVG bypasses the regex and executes JS in the app origin. **Fix:** disallow SVG or sanitize with a real XML sanitizer; serve as `attachment` with a sandboxed CSP; never `inline` SVG/HTML.

### M-5 — DNS rebinding / TOCTOU on outbound fetches
`MediaUrlValidator.java:95-113` resolves then fetches separately; used by `ShopifyMediaSyncService` and `SlackWebhookDispatcher`. A rebinding DNS answer can point the fetch at an internal address after the check passed. **Fix:** resolve once, pin the checked IP for the actual connection (custom socket factory), and disable redirects on all fetch paths (Slack already does; confirm Shopify media GET).

### M-6 — Credentialed CORS from self-service verified domains
`ApiGatewayCorsFilter.java:74-76` reflects the origin with `Access-Control-Allow-Credentials: true`; `cors-include-verified-domains` defaults `true` (`application.yml:163-165`). A tenant that verifies `attacker.com` via DNS TXT gets it added to the credentialed allowlist. **Fix:** require HTTPS-only verified origins, re-verify ownership periodically, and disable verified-domain CORS where unused (admin already defaults it off).

### M-7 — SSO endpoints not covered by application rate limiter
`RateLimitFilter.java:74-96` covers auth/showroom/webhooks but not `/oauth2/**`, `/login/oauth2/**`, `/saml2/**` (still `permitAll`, `SecurityConfig.java:100`). Docker auth limit is a very high `2000/min`. **Fix:** add SSO paths to `RateLimitFilter` + nginx `limit_req`; lower docker limits for any exposed stack.

### M-8 — Invitation token logged and returned in responses
`UserManagementService.java:182` prints the plaintext invite token to stdout; `UserController` `InviteResponse` returns both `token` and `tokenHash`. **Fix:** remove the log line (or guard behind a local flag); return the token only via the email link, never the hash.

### M-9 — Empty prod control-plane CIDR allowlist
`ops/terraform/infra/envs/prod.tfvars:14-18` ships an empty `control_plane_cidr_allowlist` (= allow-all). **Fix:** populate real VPN/office CIDRs before `terraform apply`.

---

## Low / informational

- **L-1 — 4-digit terminal PIN keyspace** (`AuthService.java:485-507`): only 10,000 combinations; offline-crackable if the hash leaks (see C-1). Mitigated online by lockout. **Fix:** longer/alphanumeric PIN, stronger per-user salt.
- **L-2 — Shopify HMAC length early-exit timing leak** (`ShopifyWebhookValidator.java:28-30`): minor oracle on signature length. **Fix:** use `MessageDigest.isEqual` on decoded bytes (as EasyPost/Stripe do).
- **L-3 — SVG/print HTML XSS in frontend** (`frontend_wms/src/stores/usePrintStore.ts:44-48`): mock label string isn't escaped on one branch. **Fix:** always route through `escapeHtml()`.
- **L-4 — AP upload uses client extension in object key** (`ApDocumentIngestionService.java:90-95`): allow-list extensions; build keys from UUID + detected type only.
- **L-5 — SPA CSP `style-src 'unsafe-inline'` and wide `img-src https:`** (frontend `nginx.conf`): tighten where feasible; parameterize `connect-src` for real origins.
- **L-6 — `oauth_callback_states` has no RLS** (`V053__...sql:28-43`): restrict to bootstrap access or store only a hash of the state.
- **L-7 — `support_tickets` RLS uses wrong GUC** (`V092__...sql:56-60` uses `app.tenant_id`; app binds `app.current_tenant`) and lacks `FORCE ROW LEVEL SECURITY`. **Fix:** align the GUC name and add `FORCE`.
- **L-8 — `EnterpriseTestController` active on `default` profile** (`:29-32`): drop `default`, restrict to `dev`/`test`.
- **L-9 — Home-realm discovery info disclosure** (`HomeRealmDiscoveryService.java:20-63`): returns tenant UUID/company/SSO URLs unauthenticated; make responses generic and rate-limit.
- **L-10 — Committed demo password SQL** (`ops/fix_passwords.sql`) and local JWT PEMs on disk (gitignored): keep out of shared images; rotate if ever used beyond local.
- **Info — Dependencies:** Spring Boot 4.1.0 / Java 25, `nimbus-jose-jwt` pinned to 10.4.2 (past CVE-2025-53864), modern React/Vite. No abandoned core frameworks; add automated SCA (OWASP Dependency-Check / Dependabot) and periodic `pnpm audit`.
- **Info — SAML** (`SamlAuthController.java`): metadata stub only; no XML parsing/signature validation present (no XXE). Either implement Spring Security SAML2 with hardened XML + signature checks, or remove the stub so clients don't treat it as functional SSO.

---

## Positive controls (validated)

- JWT RS256 only; `alg:none`/HMAC-confusion rejected; exp/iat + clock skew; impersonation/platform tokens rejected by the main filter.
- BCrypt cost 12; login lockout (IP+email) and PIN lockout.
- PostgreSQL RLS with `FORCE` on core tenant tables; `TenantContext` + `set_config('app.current_tenant', ?, true)`.
- `ProductionSecurityValidator` fails startup on mock secrets / insecure cookies / open signup under `prod`.
- CORS exact-origin (no `*` with credentials); wildcard DNS hosts rejected.
- Actuator `prometheus` blocked at public nginx and IP-gated in Spring; backends `expose`-only (not published) in compose.
- Stripe/EasyPost webhook HMAC uses constant-time comparison.
- JWT PEMs and `.env` are gitignored; `ClientIpResolver` peels `X-Forwarded-For` behind a trusted-proxy gate.
- Control plane (admin) uses CSRF cookie/header and loopback binding in compose.

---

## Remediation roadmap

**P0 — this week (network-exploitable, high impact)**
1. C-1: stop serializing `User`; return a safe DTO; add `@JsonIgnore` to hash fields; audit all `User`-returning endpoints.
2. C-2: remove the raw-secret equality branch; HMAC-only verification.
3. C-3: verify signature before any invoice lookup; identical responses for bad-sig/unknown-invoice.
4. H-4: default `expose-magic-token=false`; add it to `ProductionSecurityValidator`; disable open signup on exposed stacks.

**P1 — next sprint**
5. H-1 / H-2 / M-5: private-IP allowlist for thermal sockets, pinned Intuit/Xero base URLs, DNS-pinned outbound fetches with redirects disabled.
6. H-3: remove mock secret defaults from deployable configs; extend the prod validator to `docker`.
7. H-5: lock down Docker Compose (loopback binds, env creds, Redis AUTH, rotate/remove committed secrets).
8. M-2 / M-3: atomic OAuth-state consume; refresh-token reuse → family revocation.

**P2 — hardening**
9. H-6 + M-1: HSTS at the edge; enforce `SameSite=Strict`+`Secure` outside local; CSRF strategy for cookie auth.
10. M-4 / L-3 / L-4: SVG/upload hardening and frontend HTML escaping.
11. M-6 / M-7 / M-9: HTTPS-only verified-domain CORS, rate-limit SSO paths, populate prod CIDR allowlist.
12. L-6..L-10 + SCA: RLS GUC/`FORCE` fixes, restrict test controllers, generic home-realm responses, remove committed demo secrets, add Dependabot/OWASP Dependency-Check.

**Ongoing**
- Add regression tests for C-1/C-2/C-3 (assert no hash in invite response; assert webhook rejects raw secret; assert identical response for bad-sig vs unknown-invoice).
- Wire automated SAST/SCA and secret scanning into CI.
- Re-run this audit after P0/P1 remediation.

---

## Remediation status (2026-08-18)

Implemented in-tree (this pass). Local Docker remains a demo stack: mock webhook secrets stay in `application-dev.yml` / `application-test.yml` / compose env, not in the base `application.yml`.

| ID | Status | What changed |
|----|--------|----------------|
| C-1 | Fixed | Invitation accept returns `InviteAcceptResponse`; `@JsonIgnore` on user/admin password hashes |
| C-2 | Fixed | Accounting webhook HMAC-only; raw secret rejected |
| C-3 | Fixed | Missing invoice and bad signature both return `401 INVALID_SIGNATURE` |
| H-1 | Fixed | Printer targets block loopback/metadata/link-local; RFC1918 LAN still allowed |
| H-2 | Fixed | QuickBooks base URL pinned to Intuit hosts; realmId allowlisted |
| H-3 | Fixed | Mock secret defaults removed from deployable `application.yml` / admin base yml |
| H-4 | Fixed | `expose-magic-token` defaults false on docker; prod validator rejects it |
| H-5 | Partial | WMS/POS/API bound to `127.0.0.1`. Existing local DB/MinIO passwords left in place so volumes keep working |
| H-6 | Fixed | HSTS + Permissions-Policy on nginx edges |
| M-1 | Mitigated | Docker cookies now `SameSite=Strict`. CSRF left disabled on the data plane so existing cookie e2e keep working |
| M-2 | Fixed | OAuth state consume is a single `DELETE … RETURNING` |
| M-3 | Fixed | Reuse of a revoked refresh token revokes the whole user family |
| M-4 | Fixed | SVG/HTML/XML media served as `attachment` + `nosniff` |
| M-5 | Partial | QBO client disables redirects; media validator already rejects every private A/AAAA |
| M-6 | Already HTTPS-only | Verified-domain CORS already publishes `https://` only |
| M-7 | Fixed | `/oauth2/**`, `/login/oauth2/**`, `/saml2/**` rate-limited |
| M-8 | Fixed | Invite `tokenHash` removed from API; stdout token log removed |
| M-9 | Documented | Prod tfvars now states empty CIDR allow-list must not be applied |
| L-2 | Fixed | Shopify HMAC uses `MessageDigest.isEqual` on decoded bytes |
| L-3 | Fixed | Print-store mock labels go through `escapeHtml` |
| L-4 | Fixed | AP ingest extensions allowlisted |
| L-6 / L-7 | Fixed | Flyway `V124` — support_tickets GUC + FORCE RLS; revoke `app_user` on oauth states |
| L-8 | Fixed | `EnterpriseTestController` no longer loads on `default` |
| L-10 | Documented | `ops/fix_passwords.sql` marked local-demo-only |

Intentionally not changed (would break the running local/e2e stack): 4-digit warehouse PIN (L-1), enabling Spring CSRF on the data plane (M-1), rotating committed Postgres/MinIO/Grafana passwords (H-5 remainder), stripping tenant id from home-realm discovery (L-9 — frontend SSO buttons depend on it).
