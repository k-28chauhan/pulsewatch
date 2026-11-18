import { Link, Outlet } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { Logo } from './Logo';
import { ThemeToggle } from './ThemeToggle';

export function AppShell() {
  const { session, signOut } = useAuth();
  return (
    <>
      <header className="shell-header">
        <div className="shell-bar">
          <Link to="/" className="logo" aria-label="PulseWatch home">
            <Logo />
          </Link>
          <div className="shell-actions">
            <span className="shell-user" title={session?.email}>
              {session?.email}
            </span>
            <ThemeToggle />
            <button type="button" className="btn btn-ghost" onClick={signOut}>
              <LogOut size={15} />
              Sign out
            </button>
          </div>
        </div>
      </header>
      <main className="shell-main">
        <Outlet />
      </main>
    </>
  );
}
