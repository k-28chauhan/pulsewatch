import type { CSSProperties } from 'react';

export function Skeleton({ width = '100%', height = 14, style }: { width?: number | string; height?: number; style?: CSSProperties }) {
  return <div className="skeleton" style={{ width, height, ...style }} aria-hidden="true" />;
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="error-box" role="alert">
      <span>{message}</span>
      {onRetry && (
        <button type="button" className="btn" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
