import { useState } from 'react';
import { Moon, Sun } from 'lucide-react';

type Theme = 'light' | 'dark';

function currentTheme(): Theme {
  const explicit = document.documentElement.getAttribute('data-theme');
  if (explicit === 'light' || explicit === 'dark') return explicit;
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

/** Light/dark switch. Starts from the OS setting; once clicked, the choice is remembered. */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(currentTheme);

  function toggle() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    try {
      localStorage.setItem('pulsewatch.theme', next);
    } catch {
      /* private mode: the choice just will not persist */
    }
    setTheme(next);
  }

  return (
    <button type="button" className="btn btn-ghost btn-icon" onClick={toggle} aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}>
      {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
    </button>
  );
}
