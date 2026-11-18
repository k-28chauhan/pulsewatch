import { useEffect, useId } from 'react';

interface Props {
  title: string;
  message: string;
  confirmLabel: string;
  busy?: boolean;
  error?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({ title, message, confirmLabel, busy, error, onConfirm, onCancel }: Props) {
  const titleId = useId();

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel]);

  return (
    <div className="overlay overlay-center" onMouseDown={(event) => event.target === event.currentTarget && onCancel()}>
      <div className="dialog" role="alertdialog" aria-modal="true" aria-labelledby={titleId}>
        <h2 id={titleId}>{title}</h2>
        <p>{message}</p>
        {error && <p className="field-error">{error}</p>}
        <div className="dialog-actions">
          <button type="button" className="btn" onClick={onCancel} autoFocus>
            Cancel
          </button>
          <button type="button" className="btn btn-danger" onClick={onConfirm} disabled={busy}>
            {busy ? 'Deleting…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
