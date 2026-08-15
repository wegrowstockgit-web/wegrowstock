import { create } from 'zustand';

type AdminSessionState = {
  authenticated: boolean;
  email: string | null;
  setSession: (email: string) => void;
  clear: () => void;
};

export const useAdminSession = create<AdminSessionState>((set) => ({
  authenticated: false,
  email: null,
  setSession: (email) => set({ authenticated: true, email }),
  clear: () => set({ authenticated: false, email: null }),
}));
