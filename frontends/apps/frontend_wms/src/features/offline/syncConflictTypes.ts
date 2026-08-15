export interface ConflictFieldConstraints {
  min?: number;
  max?: number;
}

export interface ConflictFieldDescriptor {
  key: string;
  label: string;
  type: 'number' | 'string' | string;
  mutable: boolean;
  constraints?: ConflictFieldConstraints;
}

export interface ServerSyncConflict {
  id: string;
  pickerUserId?: string | null;
  pickerDisplayName?: string | null;
  actionType?: 'INBOUND_RECEIVE' | 'OUTBOUND_PICK' | 'CYCLE_COUNT' | string | null;
  actionLabel?: string | null;
  requestUrl?: string | null;
  errorMessage: string | null;
  humanSummary?: string | null;
  status: string;
  payload: {
    method?: string;
    url?: string;
    body?: unknown;
    idempotencyKey?: string;
    errorCode?: string;
  };
  schemaMetadata?: ConflictFieldDescriptor[];
  resolvedByUserId?: string | null;
  createdAt: string;
  resolvedAt?: string | null;
}
