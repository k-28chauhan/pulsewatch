import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authRequest } from '../api/hooks';
import { setAuthToken, setUnauthorizedHandler } from '../api/client';
import { decodeJwtPayload } from '../lib/jwt';

interface Session {
  token: string;
  expiresAt: string;
  email: string;
}

interface AuthState {
  session: Session | null;
  signIn: (mode: 'login' | 'register', email: string, password: string) => Promise<void>;
  signOut: () => void;
}

const STORAGE_KEY = 'pulsewatch.session';
const AuthContext = createContext<AuthState | null>(null);

/**
 * The token lives in localStorage so a page reload keeps you signed in. The trade-off: any script running on
 * this page (an XSS bug) could read it. The usual alternative, an httpOnly cookie, needs CSRF protection;
 * we chose the token because the API is stateless. Keep the app free of dangerouslySetInnerHTML.
 */
function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    return new Date(session.expiresAt).getTime() > Date.now() ? session : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => {
    const saved = loadSession();
    setAuthToken(saved?.token ?? null); // synchronously, before any child component can start a request
    return saved;
  });
  const queryClient = useQueryClient();

  const signOut = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setAuthToken(null);
    setSession(null);
    queryClient.clear(); // never leave one user's data in memory for the next
  }, [queryClient]);

  // When the server says 401 (token expired), sign out and let the router send the user to the login page.
  useEffect(() => {
    setUnauthorizedHandler(signOut);
  }, [signOut]);

  const signIn = useCallback(async (mode: 'login' | 'register', email: string, password: string) => {
    const auth = await authRequest(mode, email, password);
    const payload = decodeJwtPayload(auth.token);
    const next: Session = { token: auth.token, expiresAt: auth.expiresAt, email: String(payload?.email ?? email) };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setAuthToken(next.token); // before the next render, so the first requests already carry it
    setSession(next);
  }, []);

  const value = useMemo(() => ({ session, signIn, signOut }), [session, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
