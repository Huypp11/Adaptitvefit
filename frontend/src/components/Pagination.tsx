interface Props { page: number; totalPages: number; onChange: (p: number) => void }

export default function Pagination({ page, totalPages, onChange }: Props) {
  if (totalPages <= 1) return null;
  return (
    <div className="row">
      <button className="btn" disabled={page <= 0} onClick={() => onChange(page - 1)}>Trang trước</button>
      <span className="muted small">Trang {page + 1} / {totalPages}</span>
      <button className="btn" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Trang sau</button>
    </div>
  );
}
