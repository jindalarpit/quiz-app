import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import { api } from '@/lib/api';
import type { AuthTokens, LoginRequest, RegisterRequest, User } from '@/types';

interface AuthState {
  user: User | null;
  tokens: AuthTokens | null;
  isLoading: boolean;
  error: string | null;

  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  fetchUser: () => Promise<void>;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      tokens: null,
      isLoading: false,
      error: null,

      login: async (data: LoginRequest) => {
        set({ isLoading: true, error: null });
        try {
          const response = await api.post<{ user: User; accessToken: string; refreshToken: string }>(
            '/api/auth/login',
            data
          );
          set({
            user: response.user,
            tokens: { accessToken: response.accessToken, refreshToken: response.refreshToken },
            isLoading: false,
          });
        } catch (err: unknown) {
          const message = (err as { message?: string })?.message || 'Login failed';
          set({ error: message, isLoading: false });
          throw err;
        }
      },

      register: async (data: RegisterRequest) => {
        set({ isLoading: true, error: null });
        try {
          const response = await api.post<{ user: User; accessToken: string; refreshToken: string }>(
            '/api/auth/register',
            data
          );
          set({
            user: response.user,
            tokens: { accessToken: response.accessToken, refreshToken: response.refreshToken },
            isLoading: false,
          });
        } catch (err: unknown) {
          const message = (err as { message?: string })?.message || 'Registration failed';
          set({ error: message, isLoading: false });
          throw err;
        }
      },

      logout: () => {
        set({ user: null, tokens: null, error: null });
      },

      fetchUser: async () => {
        const { tokens } = get();
        if (!tokens) return;
        try {
          const user = await api.get<User>('/api/auth/me');
          set({ user });
        } catch {
          set({ user: null, tokens: null });
        }
      },

      clearError: () => set({ error: null }),
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({ user: state.user, tokens: state.tokens }),
    }
  )
);
