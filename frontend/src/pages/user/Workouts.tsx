import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { workoutApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import type { PageResponse, WorkoutSummary } from '../../api/types';
import Pagination from '../../components/Pagination';
import { STATUS_LABEL } from '../../lib/labels';
import { formatDateTime } from '../../lib/dates';

export default function Workouts() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<WorkoutSummary> | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    workoutApi.history(page, 15).then(setResult).catch((e) => setError(parseError(e).message));
  }, [page]);

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Buổi tập của tôi</h1>
        <p className="muted">Mở một buổi tập để xem chi tiết hoặc gửi phản hồi.</p>
      </header>
      {error && <p className="notice notice-error" role="alert">{error}</p>}
      {!result ? <p className="muted">Đang tải...</p> : result.content.length === 0 ? (
        <p className="panel muted">Chưa có buổi tập nào. <Link to="/exercises">Chọn bài tập để bắt đầu</Link>.</p>
      ) : (
        <section className="panel">
          <ul className="list">
            {result.content.map((w) => (
              <li key={w.id} className="row-between">
                <div>
                  <Link to={`/workouts/${w.id}`}><b>Buổi tập #{w.id}</b></Link>
                  <p className="muted small">Bắt đầu {formatDateTime(w.startedAt)}{w.finishedAt ? `, kết thúc ${formatDateTime(w.finishedAt)}` : ''}</p>
                </div>
                <span className={`badge ${w.status === 'COMPLETED' ? 'badge-ok' : w.status === 'STARTED' ? 'badge-warn' : ''}`}>
                  {STATUS_LABEL[w.status]}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}
    </div>
  );
}
