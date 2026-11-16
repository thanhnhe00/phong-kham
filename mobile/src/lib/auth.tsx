import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, ApiError, setToken, setUnauthorizedHandler } from './api';
import { KEYS, storage } from './storage';
import type { AuthResponse, Me } from './types';

interface AuthState {
  user: Me | null;
  /** true khi đang đọc phiên đã lưu lúc mở app */
  loading: boolean;
  login: (phone: string, password: string) => Promise<void>;
  register: (phone: string, code: string, password: string) => Promise<void>;
  resetPassword: (phone: string, code: string, newPassword: string) => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  const logout = useCallback(async () => {
    setToken(null);
    await storage.set(KEYS.token, null);
    await storage.set(KEYS.patient, null);
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(() => void logout());
    return () => {
      setUnauthorizedHandler(null);
    };
  }, [logout]);

  // Mở app: dùng phiên đã lưu để vào thẳng
  useEffect(() => {
    (async () => {
      const saved = await storage.get(KEYS.token);
      if (!saved) return;
      setToken(saved);
      try {
        const me = await api.get<Me>('/auth/me');
        if (!me.roles.includes('PATIENT')) return await logout();
        setUser(me);
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) await logout();
        else setToken(null); // mất mạng: về màn đăng nhập, phiên vẫn còn lưu cho lần mở sau
      }
    })().finally(() => setLoading(false));
  }, [logout]);

  const accept = useCallback(async (res: AuthResponse) => {
    if (!res.user.roles.includes('PATIENT')) {
      throw new ApiError('Ứng dụng dành cho bệnh nhân. Nhân viên vui lòng dùng trang web của phòng khám.', 403);
    }
    setToken(res.token);
    await storage.set(KEYS.token, res.token);
    setUser(res.user);
  }, []);

  const login = useCallback(
    async (phone: string, password: string) => accept(await api.post<AuthResponse>('/auth/login', { phone, password })),
    [accept],
  );
  const register = useCallback(
    async (phone: string, code: string, password: string) =>
      accept(await api.post<AuthResponse>('/auth/register', { phone, code, password })),
    [accept],
  );
  const resetPassword = useCallback(
    async (phone: string, code: string, newPassword: string) => {
      await api.post('/auth/password/reset', { phone, code, newPassword });
      await accept(await api.post<AuthResponse>('/auth/login', { phone, password: newPassword }));
    },
    [accept],
  );
  const changePassword = useCallback(
    async (currentPassword: string, newPassword: string) =>
      accept(await api.post<AuthResponse>('/auth/password/change', { currentPassword, newPassword })),
    [accept],
  );

  const value = useMemo(
    () => ({ user, loading, login, register, resetPassword, changePassword, logout }),
    [user, loading, login, register, resetPassword, changePassword, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth phải nằm trong AuthProvider');
  return ctx;
}
