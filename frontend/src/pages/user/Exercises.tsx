import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { exerciseApi, profileApi, workoutApi } from '../../api/endpoints';
import { orNull, parseError } from '../../api/errors';
import type { Exercise, PageResponse } from '../../api/types';
import ExerciseCard from '../../components/ExerciseCard';
import ExerciseGuide from '../../components/ExerciseGuide';
import Pagination from '../../components/Pagination';
import { INTENSITIES, LOCATIONS } from '../../lib/labels';

export default function Exercises() {
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [tag, setTag] = useState('');
  const [location, setLocation] = useState('');
  const [intensity, setIntensity] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<Exercise> | null>(null);
  const [selected, setSelected] = useState<number[]>([]);
  const [error, setError] = useState('');
  const [conflict, setConflict] = useState(false);
  const [starting, setStarting] = useState(false);
  const [weightKg, setWeightKg] = useState<number | null>(null);
  const [guide, setGuide] = useState<Exercise | null>(null);

  useEffect(() => {
    orNull(profileApi.get()).then((p) => setWeightKg(p ? p.weightKg : null)).catch(() => undefined);
  }, []);

  useEffect(() => {
    const t = setTimeout(() => {
      exerciseApi
        .list({ q: q || undefined, tag: tag || undefined, location: location || undefined, intensity: intensity || undefined, page, size: 12 })
        .then((r) => { setResult(r); setError(''); })
        .catch((e) => setError(parseError(e).message));
    }, 250);
    return () => clearTimeout(t);
  }, [q, tag, location, intensity, page]);

  const toggle = (id: number) =>
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : s.length >= 10 ? s : [...s, id]));

  async function start() {
    setError(''); setConflict(false); setStarting(true);
    try {
      const w = await workoutApi.start(selected);
      navigate(`/workouts/${w.id}`);
    } catch (err) {
      const info = parseError(err);
      setError(info.message);
      setConflict(info.status === 409);
    } finally {
      setStarting(false);
    }
  }

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Thư viện bài tập</h1>
        <p className="muted">Chọn tối đa 10 bài, rồi bắt đầu buổi tập.</p>
      </header>

      <div className="panel">
        <div className="form-grid">
          <div className="field">
            <label htmlFor="q">Tìm theo tên</label>
            <input id="q" className="input" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
          </div>
          <div className="field">
            <label htmlFor="tag">Thẻ</label>
            <input id="tag" className="input" placeholder="ví dụ: recovery" value={tag}
                   onChange={(e) => { setTag(e.target.value); setPage(0); }} />
          </div>
          <div className="field">
            <label htmlFor="loc">Nơi tập</label>
            <select id="loc" className="input" value={location} onChange={(e) => { setLocation(e.target.value); setPage(0); }}>
              <option value="">Tất cả</option>
              {LOCATIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="int">Cường độ</label>
            <select id="int" className="input" value={intensity} onChange={(e) => { setIntensity(e.target.value); setPage(0); }}>
              <option value="">Tất cả</option>
              {INTENSITIES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>
        </div>
      </div>

      {error && (
        <p className="notice notice-error" role="alert">
          {error} {conflict && <Link to="/workouts">Xem buổi tập của tôi</Link>}
        </p>
      )}

      {!result ? <p className="muted">Đang tải...</p> : result.content.length === 0 ? (
        <p className="muted">Không có bài tập nào khớp bộ lọc. Hãy bỏ bớt điều kiện lọc.</p>
      ) : (
        <div className="grid-2">
          {result.content.map((e) => (
            <ExerciseCard key={e.id} exercise={e} weightKg={weightKg} selected={selected.includes(e.id)} onToggle={toggle} onOpenGuide={setGuide} />
          ))}
        </div>
      )}

      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}

      {guide && <ExerciseGuide exercise={guide} weightKg={weightKg} onClose={() => setGuide(null)} />}

      {selected.length > 0 && (
        <div className="pickbar" role="region" aria-label="Bài tập đã chọn">
          <span>Đã chọn {selected.length} bài</span>
          <div className="row">
            <button className="btn btn-quiet" style={{ color: '#fff' }} onClick={() => setSelected([])}>Bỏ chọn</button>
            <button className="btn btn-primary" disabled={starting} onClick={start}>
              {starting ? 'Đang bắt đầu...' : 'Bắt đầu buổi tập'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
