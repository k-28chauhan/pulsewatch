import { useEffect, useId, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

interface Props {
  title: string;
  onClose: () => void;
  children: ReactNode;
}

/**
 * A side sheet. Closes on Escape or a click on the dim background, moves keyboard focus inside when it
 * opens and gives it back to the button that opened it when it closes.
 */
export function Drawer({ title, onClose, children }: Props) {
  const titleId = useId();
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    panel.current?.querySelector<HTMLElement>('input, select, textarea, button')?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      opener?.focus?.();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once on open/close
  }, []);

  return (
    <div className="overlay" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div className="drawer" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={panel}>
        <div className="drawer-head">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="btn btn-ghost btn-icon" onClick={onClose} aria-label="Close">
            <X size={16} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
