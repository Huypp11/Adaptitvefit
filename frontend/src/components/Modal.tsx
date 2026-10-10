import { useEffect, type ReactNode } from 'react';

interface Props { title: string; onClose: () => void; children: ReactNode }

export default function Modal({ title, onClose, children }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="row-between">
          <h2>{title}</h2>
          <button type="button" className="btn btn-quiet" onClick={onClose}>Đóng</button>
        </div>
        {children}
      </div>
    </div>
  );
}
