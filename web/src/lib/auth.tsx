import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, getToken, setToken, setUnauthorizedHandler } from './api';
import type { AuthResponse, Me, Role } from './types';

interface AuthState {
  me: Me | null;
  loading: boolean;
  login(phone: string, password: string): Promise<void>;
  changePassword(currentPassword: string, newPassword: string): Promise<void>;
  logout(): void;
  has(...roles: Role[]): boolean;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(!!getToken());

  const logout = useCallback(() => {
    setToken(null);
    setMe(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    if (!getToken()) return;
    api.get<Me>('/auth/me').then(setMe).catch(logout).finally(() => setLoading(false));
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  const value = useMemo<AuthState>(() => ({
    me,
    loading,
    async login(phone, password) {
      const r = await api.post<AuthResponse>('/auth/login', { phone: phone.replace(/\s/g, ''), password });
      if (r.user.roles.every((x) => x === 'PATIENT')) {
        throw new Error('Tài khoản bệnh nhân dùng ứng dụng trên điện thoại, không đăng nhập ở đây.');
      }
      setToken(r.token);
      setMe(r.user);
    },
    async changePassword(currentPassword, newPassword) {
      const r = await api.post<AuthResponse>('/auth/password/change', { currentPassword, newPassword });
      setToken(r.token);
      setMe(r.user);
    },
    logout,
    has: (...roles) => !!me && roles.some((r) => me.roles.includes(r)),
  }), [me, loading, logout]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth ngoài AuthProvider');
  return v;
}
