import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { SsoConfig, TenantEmailDomain } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { TableSkeleton } from '@/components/ui/Skeleton';

function SavedNote({ show }: { show: boolean }) {
  if (!show) return null;
  return <span className="text-sm text-success">Saved</span>;
}

export function TenantSecuritySettings() {
  const queryClient = useQueryClient();
  const [issuerUrl, setIssuerUrl] = useState('');
  const [clientId, setClientId] = useState('');
  const [clientSecret, setClientSecret] = useState('');
  const [enabled, setEnabled] = useState(false);
  const [forceSso, setForceSso] = useState(false);
  const [protocol, setProtocol] = useState<'OIDC' | 'SAML'>('OIDC');
  const [ssoProvider, setSsoProvider] = useState('CUSTOM');
  const [samlMetadataUrl, setSamlMetadataUrl] = useState('');
  const [samlEntityId, setSamlEntityId] = useState('');
  const [acsUrl, setAcsUrl] = useState('');
  const [samlCertificate, setSamlCertificate] = useState('');
  const [cidrDraft, setCidrDraft] = useState('');
  const [corporateCidrs, setCorporateCidrs] = useState<string[]>([]);
  const [domainName, setDomainName] = useState('');
  const [error, setError] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['settings', 'sso'],
    queryFn: async () => (await apiClient.get<SsoConfig>('/api/v1/settings/sso')).data,
    retry: false,
  });

  const { data: connectionStates } = useQuery({
    queryKey: ['settings', 'sso', 'connection-states'],
    queryFn: async () =>
      (
        await apiClient.get<{
          providers: Array<{
            id: string;
            displayName: string;
            status: string;
            connected: boolean;
            issuerTemplate: string;
          }>;
        }>('/api/v1/settings/sso/connection-states')
      ).data,
    retry: false,
  });

  const { data: domains = [] } = useQuery({
    queryKey: ['email-domains'],
    queryFn: async () => (await apiClient.get<TenantEmailDomain[]>('/api/v1/settings/email-domains')).data,
    retry: false,
  });

  useEffect(() => {
    if (!data) return;
    setIssuerUrl(data.issuerUrl);
    setClientId(data.clientId);
    setEnabled(data.enabled);
    setForceSso(data.forceSso);
    setProtocol(data.protocol === 'SAML' ? 'SAML' : 'OIDC');
    setSsoProvider(data.ssoProvider || data.provider || 'CUSTOM');
    setSamlMetadataUrl(data.samlMetadataUrl ?? '');
    setSamlEntityId(data.samlEntityId ?? '');
    setAcsUrl(data.acsUrl ?? '');
    setSamlCertificate(data.samlCertificate ?? '');
    setCorporateCidrs(data.corporateCidrIps ?? []);
  }, [data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      await apiClient.put('/api/v1/settings/sso', {
        issuerUrl,
        clientId,
        clientSecret: clientSecret || undefined,
        enabled,
        forceSso,
        enforceSso: forceSso,
        protocol,
        ssoProvider,
        samlMetadataUrl: samlMetadataUrl || undefined,
        samlEntityId: samlEntityId || undefined,
        acsUrl: acsUrl || undefined,
        samlCertificate: samlCertificate || undefined,
        corporateCidrIps: corporateCidrs,
      });
    },
    onSuccess: () => {
      setClientSecret('');
      setError('');
      void queryClient.invalidateQueries({ queryKey: ['settings', 'sso'] });
    },
    onError: () => setError('Could not save SSO settings. Check issuer URL, client ID, secret, and CIDRs.'),
  });

  const registerDomain = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/settings/email-domains', { domainName });
    },
    onSuccess: () => {
      setDomainName('');
      void queryClient.invalidateQueries({ queryKey: ['email-domains'] });
    },
  });

  const verifyDomain = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/settings/email-domains/${id}/verify`);
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['email-domains'] }),
  });

  const addCidr = () => {
    const next = cidrDraft.trim();
    if (!next || corporateCidrs.includes(next)) return;
    setCorporateCidrs((prev) => [...prev, next]);
    setCidrDraft('');
  };

  if (isLoading) return <TableSkeleton rows={4} cols={2} />;

  return (
    <div className="space-y-6" data-testid="security-sso-tab">
      <Card>
        <CardHeader
          title="Corporate domains"
          description="Verify a DNS TXT record so Home Realm Discovery can route @company emails to this workspace"
        />
        <form
          className="mb-4 flex flex-col gap-3 sm:flex-row"
          onSubmit={(e) => {
            e.preventDefault();
            registerDomain.mutate();
          }}
        >
          <Input
            label="Domain name"
            value={domainName}
            onChange={(e) => setDomainName(e.target.value)}
            placeholder="acme.com"
            data-testid="hrd-domain-input"
          />
          <div className="flex items-end">
            <Button type="submit" loading={registerDomain.isPending} data-testid="hrd-domain-add">
              Register domain
            </Button>
          </div>
        </form>
        <div className="space-y-3" data-testid="hrd-domain-list">
          {domains.length === 0 ? (
            <p className="text-sm text-text-muted">No corporate domains registered yet.</p>
          ) : (
            domains.map((domain) => (
              <div
                key={domain.id}
                className="rounded-lg border border-border bg-surface-raised p-3"
                data-testid={`hrd-domain-${domain.domainName}`}
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="font-semibold text-text">{domain.domainName}</p>
                    <p className="text-xs text-text-muted">
                      {domain.isVerified || domain.verificationStatus === 'ACTIVE' || domain.verificationStatus === 'VERIFIED'
                        ? 'Verified'
                        : domain.verificationStatus}
                    </p>
                  </div>
                  {!(domain.isVerified || domain.verificationStatus === 'ACTIVE' || domain.verificationStatus === 'VERIFIED') ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      data-testid={`hrd-domain-verify-${domain.id}`}
                      loading={verifyDomain.isPending}
                      onClick={() => verifyDomain.mutate(domain.id)}
                    >
                      Verify DNS
                    </Button>
                  ) : null}
                </div>
                {domain.dnsVerificationToken ? (
                  <p className="mt-2 break-all font-mono text-xs text-text" data-testid={`hrd-domain-txt-${domain.id}`}>
                    TXT @ → {domain.dnsVerificationToken}
                  </p>
                ) : null}
              </div>
            ))
          )}
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Identity providers"
          description="OAuth2 / OIDC connection state for Google Workspace, Entra ID, and Okta"
        />
        <div className="grid gap-3 sm:grid-cols-3" data-testid="sso-connection-cards">
          {(
            connectionStates?.providers ?? [
              { id: 'GOOGLE', displayName: 'Google Workspace', status: 'DISCONNECTED', connected: false, issuerTemplate: '' },
              { id: 'ENTRA', displayName: 'Microsoft Entra ID', status: 'DISCONNECTED', connected: false, issuerTemplate: '' },
              { id: 'OKTA', displayName: 'Okta', status: 'DISCONNECTED', connected: false, issuerTemplate: '' },
            ]
          ).map((p) => (
            <button
              key={p.id}
              type="button"
              data-testid={`sso-card-${p.id}`}
              className="rounded-lg border border-border bg-surface-raised p-4 text-left transition hover:border-accent"
              onClick={() => {
                if (p.issuerTemplate && !p.issuerTemplate.includes('{')) {
                  setIssuerUrl(p.issuerTemplate);
                } else if (p.id === 'GOOGLE') {
                  setIssuerUrl('https://accounts.google.com');
                } else if (p.id === 'ENTRA') {
                  setIssuerUrl('https://login.microsoftonline.com/common/v2.0');
                } else if (p.id === 'OKTA') {
                  setIssuerUrl('https://your-org.okta.com/oauth2/default');
                }
                setProtocol('OIDC');
                setSsoProvider(p.id);
              }}
            >
              <div className="text-sm font-semibold text-text">{p.displayName}</div>
              <div
                className={`mt-2 text-xs font-medium ${p.connected ? 'text-success' : 'text-text-muted'}`}
                data-testid={`sso-status-${p.id}`}
              >
                {p.status}
              </div>
            </button>
          ))}
        </div>
      </Card>

      <Card>
        <CardHeader
          title="SSO configuration"
          description="SAML / OIDC details. Enforce SSO to skip the password field after Home Realm Discovery."
        />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setError('');
            saveMutation.mutate();
          }}
          className="space-y-4"
        >
          <Select
            label="SSO provider"
            value={ssoProvider}
            onChange={(e) => setSsoProvider(e.target.value)}
            data-testid="sso-provider"
          >
            <option value="GOOGLE">Google Workspace</option>
            <option value="ENTRA">Microsoft Entra ID</option>
            <option value="OKTA">Okta</option>
            <option value="CUSTOM_SAML">Custom SAML</option>
            <option value="CUSTOM">Custom OIDC</option>
          </Select>
          <label className="block space-y-1.5">
            <span className="text-sm font-medium text-text">Protocol</span>
            <select
              value={protocol}
              onChange={(e) => {
                const next = e.target.value === 'SAML' ? 'SAML' : 'OIDC';
                setProtocol(next);
                if (next === 'SAML') setSsoProvider('CUSTOM_SAML');
              }}
              className="h-10 w-full rounded-md border border-border bg-surface-raised px-3 text-sm"
            >
              <option value="OIDC">OIDC (OAuth2)</option>
              <option value="SAML">SAML 2.0</option>
            </select>
          </label>
          <Input
            label="Issuer URL"
            value={issuerUrl}
            onChange={(e) => setIssuerUrl(e.target.value)}
            placeholder="https://your-org.okta.com/oauth2/default"
            required={protocol === 'OIDC'}
          />
          {protocol === 'SAML' && (
            <>
              <Input
                label="SAML metadata URL"
                value={samlMetadataUrl}
                onChange={(e) => setSamlMetadataUrl(e.target.value)}
              />
              <Input
                label="Entity ID"
                value={samlEntityId}
                onChange={(e) => setSamlEntityId(e.target.value)}
                data-testid="sso-entity-id"
              />
              <Input
                label="ACS URL"
                value={acsUrl}
                onChange={(e) => setAcsUrl(e.target.value)}
                placeholder="https://app.wegrowstock.com/saml2/sso"
                data-testid="sso-acs-url"
              />
              <label className="block space-y-1.5">
                <span className="text-sm font-medium text-text">X.509 certificate</span>
                <textarea
                  value={samlCertificate}
                  onChange={(e) => setSamlCertificate(e.target.value)}
                  data-testid="sso-saml-certificate"
                  className="min-h-28 w-full rounded-md border border-border bg-surface-raised px-3 py-2 font-mono text-xs"
                  placeholder="-----BEGIN CERTIFICATE-----"
                />
              </label>
            </>
          )}
          <Input
            label="Client ID"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            required={protocol === 'OIDC'}
          />
          <Input
            label="Client secret"
            type="password"
            value={clientSecret}
            onChange={(e) => setClientSecret(e.target.value)}
            placeholder={data?.configured ? 'Leave blank to keep existing secret' : 'Required on first save'}
            required={!data?.configured && protocol === 'OIDC'}
          />
          <label className="flex items-center gap-3" htmlFor="sso-enabled">
            <input
              id="sso-enabled"
              name="ssoEnabled"
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
              className="h-4 w-4 rounded border-border accent-accent"
            />
            <span className="text-sm text-text">Enable SSO for this workspace</span>
          </label>
          <label className="flex items-center gap-3" htmlFor="sso-force">
            <input
              id="sso-force"
              name="forceCorporateSso"
              type="checkbox"
              checked={forceSso}
              disabled={!enabled}
              onChange={(e) => setForceSso(e.target.checked)}
              className="h-4 w-4 rounded border-border accent-accent disabled:opacity-50"
              data-testid="sso-enforce"
            />
            <span className="text-sm text-text">Enforce SSO for all corporate users</span>
          </label>
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex items-center gap-3">
            <Button type="submit" loading={saveMutation.isPending}>
              Save SSO settings
            </Button>
            <SavedNote show={saveMutation.isSuccess && !saveMutation.isPending} />
          </div>
        </form>
      </Card>

      <Card>
        <CardHeader
          title="Warehouse network"
          description="CIDR blocks (for example 203.0.113.0/24) skip email matching and send the register to this tenant’s SSO"
        />
        <div className="flex flex-col gap-3 sm:flex-row">
          <Input
            label="Add CIDR"
            value={cidrDraft}
            onChange={(e) => setCidrDraft(e.target.value)}
            placeholder="203.0.113.0/24"
            data-testid="hrd-cidr-input"
          />
          <div className="flex items-end">
            <Button type="button" variant="secondary" onClick={addCidr} data-testid="hrd-cidr-add">
              Add CIDR
            </Button>
          </div>
        </div>
        <ul className="mt-3 space-y-2" data-testid="hrd-cidr-list">
          {corporateCidrs.map((cidr) => (
            <li key={cidr} className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
              <span className="font-mono">{cidr}</span>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                onClick={() => setCorporateCidrs((prev) => prev.filter((item) => item !== cidr))}
              >
                Remove
              </Button>
            </li>
          ))}
        </ul>
        <p className="mt-3 text-xs text-text-muted">Save SSO settings to persist the CIDR list.</p>
      </Card>
    </div>
  );
}
