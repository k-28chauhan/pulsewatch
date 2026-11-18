import { useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Logo } from '../components/Logo';
import { ThemeToggle } from '../components/ThemeToggle';

export function LoginPage() {
  const { session, signIn } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  if (session) return <Navigate to="/" replace />;

  const registering = mode === 'register';

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    if (registering && password.length < 8) {
      setFieldErrors({ password: 'Use at least 8 characters' });
      return;
    }
    setBusy(true);
    try {
      await signIn(mode, email, password);
    } catch (e) {
      if (e instanceof ApiError) {
        setFieldErrors(e.fieldErrors);
        if (Object.keys(e.fieldErrors).length === 0) setError(e.message);
      } else {
        setError('Something went wrong. Please try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth">
      <div style={{ position: 'fixed', top: 12, right: 16 }}>
        <ThemeToggle />
      </div>
      <div className="auth-card">
        <Logo size={26} />
        <div>
          <h1>{registering ? 'Create your account' : 'Welcome back'}</h1>
          <p className="muted">{registering ? 'Start watching your first endpoint in a minute.' : 'Sign in to see how your endpoints are doing.'}</p>
        </div>

        <form className="auth-form" onSubmit={submit} noValidate>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input id="email" className="input" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required aria-invalid={!!fieldErrors.email} autoFocus />
            {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input id="password" className="input" type="password" autoComplete={registering ? 'new-password' : 'current-password'} value={password} onChange={(e) => setPassword(e.target.value)} required aria-invalid={!!fieldErrors.password} />
            {fieldErrors.password ? <span className="field-error">{fieldErrors.password}</span> : registering && <span className="field-hint">At least 8 characters.</span>}
          </div>
          {error && <p className="field-error" role="alert">{error}</p>}
          <button type="submit" className="btn btn-primary btn-lg" disabled={busy || !email || !password}>
            {busy ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}
          </button>
        </form>

        <p className="auth-switch">
          {registering ? 'Already have an account? ' : 'New to PulseWatch? '}
          <button type="button" className="link-btn" onClick={() => { setMode(registering ? 'login' : 'register'); setError(null); setFieldErrors({}); }}>
            {registering ? 'Sign in' : 'Create an account'}
          </button>
        </p>
      </div>
    </div>
  );
}
