import { create } from 'zustand';
import type { AdminProfile } from '@/lib/types/auth';

interface AuthState {
    user: AdminProfile | null;
    loading: boolean;
    initialized: boolean;
    setUser: (user: AdminProfile | null) => void;
    setLoading: (loading: boolean) => void;
    setInitialized: (initialized: boolean) => void;
    logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
    user: null,
    loading: true,
    initialized: false,
    setUser: (user) => set({ user, loading: false }),
    setLoading: (loading) => set({ loading }),
    setInitialized: (initialized) => set({ initialized }),
    logout: () => set({ user: null, initialized: false }),
}));
