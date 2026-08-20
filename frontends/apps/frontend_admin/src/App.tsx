import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AdminLayout } from '@/features/layout/AdminLayout';
import { AdminLoginPage } from '@/features/auth/AdminLoginPage';
import { TenantManager } from '@/features/tenants/TenantManager';
import { AdminCommercialReports } from '@/features/reports/AdminCommercialReports';
import { AdminHealthReports } from '@/features/reports/AdminHealthReports';
import { PlatformBillingPanel } from '@/features/billing/PlatformBillingPanel';
import { CopilotKnowledgeManager } from '@/features/copilot/CopilotKnowledgeManager';
import { IntegrationsHubPanel } from '@/features/integrations/IntegrationsHubPanel';
import { PlatformAuditTrail } from '@/features/audit/PlatformAuditTrail';
import { ShardRoutingPanel } from '@/features/infrastructure/ShardRoutingPanel';
import { DeadLetterQueuePanel } from '@/features/operations/DeadLetterQueuePanel';
import { ConcurrencyDashboard } from '@/features/telemetry/ConcurrencyDashboard';
import { FeatureFlagPanel } from '@/features/flags/FeatureFlagPanel';
import { GlobalCompliancePanel } from '@/features/compliance/GlobalCompliancePanel';
import { PlatformPackagingPanel } from '@/features/packaging/PlatformPackagingPanel';
import { useAdminSession } from '@/features/auth/adminSession';

function LoginRoute() {
  const authenticated = useAdminSession((s) => s.authenticated);
  if (authenticated) {
    return <Navigate to="/" replace />;
  }
  return <AdminLoginPage />;
}

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginRoute />} />
        <Route path="/" element={<AdminLayout />}>
          <Route index element={<TenantManager />} />
          <Route path="billing" element={<PlatformBillingPanel />} />
          <Route path="packaging" element={<PlatformPackagingPanel />} />
          <Route path="copilot/knowledge" element={<CopilotKnowledgeManager />} />
          <Route path="integrations" element={<IntegrationsHubPanel />} />
          <Route path="audit" element={<PlatformAuditTrail />} />
          <Route path="shards" element={<ShardRoutingPanel />} />
          <Route path="operations/dlq" element={<DeadLetterQueuePanel />} />
          <Route path="telemetry" element={<ConcurrencyDashboard />} />
          <Route path="flags" element={<FeatureFlagPanel />} />
          <Route path="compliance" element={<GlobalCompliancePanel />} />
          <Route path="reports/commercial" element={<AdminCommercialReports />} />
          <Route path="reports/health" element={<AdminHealthReports />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
