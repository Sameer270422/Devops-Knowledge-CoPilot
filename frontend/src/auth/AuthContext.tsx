import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setAccessToken, setOnAuthExpired } from '../api/client';

interface AuthState {
  email: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  email: string;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [email, setEmail] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    setOnAuthExpired(() => setEmail(null));
    // On page load there's no access token in memory yet (by design — it's never
    // persisted). Try the refresh cookie once to silently resume a session.
    api
      .post<AuthResponse>('/api/auth/refresh')
      .then((res) => {
        setAccessToken(res.accessToken);
        setEmail(res.email);
      })
      .catch(() => setAccessToken(null))
      .finally(() => setIsLoading(false));
  }, []);

  const login = async (loginEmail: string, password: string) => {
    const res = await api.post<AuthResponse>('/api/auth/login', { email: loginEmail, password });
    setAccessToken(res.accessToken);
    setEmail(res.email);
  };

  const register = async (registerEmail: string, password: string) => {
    await api.post<void>('/api/auth/register', { email: registerEmail, password });
    await login(registerEmail, password);
  };

  const logout = async () => {
    await api.post<void>('/api/auth/logout').catch(() => undefined);
    setAccessToken(null);
    setEmail(null);
  };

  const value = useMemo(
    () => ({ email, isAuthenticated: email !== null, isLoading, login, register, logout }),
    [email, isLoading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
