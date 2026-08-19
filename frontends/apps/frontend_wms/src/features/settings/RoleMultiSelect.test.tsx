import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { RoleMultiSelect } from '@/features/settings/RoleMultiSelect';
import { roleApi } from '@/api/roles';

vi.mock('@/api/roles', () => ({
  roleApi: {
    list: vi.fn(),
  },
}));

const ROLES = [
  {
    id: 'id-admin',
    name: 'ADMIN',
    isSystemRole: true,
    description: 'Full warehouse administration except ownership transfer',
  },
  {
    id: 'id-picker',
    name: 'PICKER',
    isSystemRole: true,
    description: 'Pick, pack, and put-away',
  },
  {
    id: 'id-viewer',
    name: 'VIEWER',
    isSystemRole: true,
    description: 'Read-only operations',
  },
  {
    id: 'id-owner',
    name: 'OWNER',
    isSystemRole: true,
    description: 'Tenant owner — cannot be assigned from this list',
  },
  {
    id: 'id-custom',
    name: 'JUNIOR_BUYER',
    isSystemRole: false,
    description: null,
  },
];

function wrap(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('RoleMultiSelect', () => {
  beforeEach(() => {
    vi.mocked(roleApi.list).mockReset();
    vi.mocked(roleApi.list).mockResolvedValue(ROLES);
  });

  it('renders live descriptions and checks additive roles', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    wrap(<RoleMultiSelect value={['VIEWER']} onChange={onChange} />);

    expect(await screen.findByText('Pick, pack, and put-away')).toBeInTheDocument();
    expect(screen.getByText('Custom organizational role')).toBeInTheDocument();

    await user.click(screen.getByTestId('role-option-PICKER').querySelector('input')!);
    expect(onChange).toHaveBeenCalledWith(['VIEWER', 'PICKER']);
  });

  it('unchecks a selected live role', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    wrap(<RoleMultiSelect value={['VIEWER', 'PICKER']} onChange={onChange} />);
    await user.click((await screen.findByTestId('role-option-VIEWER')).querySelector('input')!);
    expect(onChange).toHaveBeenCalledWith(['PICKER']);
  });

  it('keeps OWNER locked when included', async () => {
    wrap(<RoleMultiSelect value={['OWNER']} onChange={vi.fn()} includeCodes={['OWNER']} />);
    expect((await screen.findByTestId('role-option-OWNER')).querySelector('input')).toBeDisabled();
  });
});
