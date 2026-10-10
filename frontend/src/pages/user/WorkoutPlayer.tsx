import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { profileApi, workoutApi } from '../../api/endpoints';
import { orNull, parseError } from '../../api/errors';
import type { Workout } from '../../api/types';
import ExerciseImage from '../../components/ExerciseImage';
import { estimateKcal, formatClock, metFor } from '../../lib/calories';

const REST_SECONDS = 30;
type Phase = 'ready' | 'work' | 'rest' | 'done';

export default function WorkoutPlayer() {
  const { id } = useParams();
  const workoutId = Number(id);
  const navigate = useNavigate();

  const [workout, setWorkout] = useState<Workout | null>(null);
  const [weightKg, setWeightKg] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const [phase, setPhase] = useState<Phase>('ready');
  const [idx, setIdx] = useState(0);
  const [left, setLeft] = useState(0);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (!Number.isFinite(workoutId)) { setError('Đường dẫn buổi tập không hợp lệ'); return; }
    workoutApi.get(workoutId).then(setWorkout).catch((e) => setError(parseError(e).message));
    orNull(profileApi.get()).then((p) => setWeightKg(p ? p.weightKg : null)).catch(() => undefined);
  }, [workoutId]);

  // Dem nguoc moi giay khi dang tap hoac nghi va khong tam dung
  useEffect(() => {
    if ((phase !== 'work' && phase !== 'rest') || paused) return;
    const t = setInterval(() => setLeft((l) => l - 1), 1000);
    return () => clearInterval(t);
  }, [phase, paused]);

  // Het gio: chuyen sang nghi / bai tiep / ket thuc
  useEffect(() => {
    if (!workout || left > 0) return;
    if (phase === 'work') {
      if (idx >= workout.exercises.length - 1) setPhase('done');
      else { setPhase('rest'); setLeft(REST_SECONDS); }
    } else if (phase === 'rest') {
      const next = idx + 1;
      setIdx(next);
      setPhase('work');
      setLeft(workout.exercises[next].plannedMin * 60);
    }
  }, [left, phase, idx, workout]);

  if (!workout) {
    return error
      ? <div className="stack"><p className="notice notice-error" role="alert">{error}</p><Link to="/workouts">Về danh sách buổi tập</Link></div>
      : <p className="muted">Đang tải...</p>;
  }
  if (workout.status !== 'STARTED') return <Navigate to={`/workouts/${workout.id}`} replace />;

  const items = workout.exercises;
  const current = items[idx];
  const totalSeconds = items.reduce((s, x) => s + x.plannedMin * 60, 0);
  const totalKcal = weightKg
    ? items.reduce((s, x) => s + estimateKcal(metFor(x.met, x.intensity), weightKg, x.plannedMin), 0)
    : null;

  function begin() {
    setIdx(0);
    setPhase('work');
    setLeft(items[0].plannedMin * 60);
    setPaused(false);
  }

  async function finish() {
    setError(''); setBusy(true);
    try {
      await workoutApi.complete(workoutId);
      navigate(`/workouts/${workoutId}`, { replace: true });
    } catch (e) {
      setError(parseError(e).message);
      setBusy(false);
    }
  }

  if (phase === 'ready') {
    return (
      <div className="stack-lg">
        <header className="page-head">
          <h1>Sẵn sàng tập?</h1>
          <p className="muted">
            {items.length} bài, khoảng {Math.round(totalSeconds / 60)} phút
            {totalKcal !== null ? `, ước tính ${totalKcal} kcal` : ''}. Giữa các bài có {REST_SECONDS} giây nghỉ.
          </p>
        </header>
        <section className="panel">
          <ol className="steps">
            {items.map((x) => <li key={x.exerciseId}>{x.name} ({x.plannedMin} phút)</li>)}
          </ol>
        </section>
        <div className="row">
          <button className="btn btn-primary" onClick={begin}>Bắt đầu</button>
          <Link className="btn" to={`/workouts/${workout.id}`}>Quay lại</Link>
        </div>
      </div>
    );
  }

  if (phase === 'done') {
    return (
      <div className="stack-lg">
        <header className="page-head">
          <h1>Bạn đã đi hết các bài</h1>
          <p className="muted">
            Tổng thời gian dự kiến {Math.round(totalSeconds / 60)} phút
            {totalKcal !== null ? `, ước tính khoảng ${totalKcal} kcal (chỉ là ước lượng)` : ''}.
          </p>
        </header>
        {error && <p className="notice notice-error" role="alert">{error}</p>}
        <div className="row">
          <button className="btn btn-primary" disabled={busy} onClick={finish}>Hoàn thành buổi tập</button>
          <Link className="btn" to={`/workouts/${workout.id}`}>Để sau</Link>
        </div>
      </div>
    );
  }

  const resting = phase === 'rest';
  const shown = resting ? items[idx + 1] : current;
  const doneSeconds = items.slice(0, idx).reduce((s, x) => s + x.plannedMin * 60, 0);
  const pct = Math.min(100, Math.round(((doneSeconds + (resting ? items[idx].plannedMin * 60 : items[idx].plannedMin * 60 - left)) / totalSeconds) * 100));

  return (
    <div className="player">
      <div className="progress-bar" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}><i style={{ width: `${pct}%` }} /></div>
      <p className="muted">Bài {idx + 1}/{items.length}</p>

      <div className="stack" style={{ gap: 8 }}>
        <h1>{resting ? 'Nghỉ một chút' : current.name}</h1>
        {resting && <p className="muted">Bài tiếp theo: <b>{shown.name}</b></p>}
        <div className={`clock ${resting ? 'rest' : ''}`} aria-live="off">{formatClock(left)}</div>
      </div>

      <div className="row">
        <button className="btn btn-primary" onClick={() => setPaused((p) => !p)}>{paused ? 'Tiếp tục' : 'Tạm dừng'}</button>
        <button className="btn" onClick={() => setLeft(0)}>{resting ? 'Bỏ qua nghỉ' : idx >= items.length - 1 ? 'Kết thúc bài' : 'Sang bài tiếp'}</button>
        <Link className="btn btn-quiet" to={`/workouts/${workout.id}`}>Thoát</Link>
      </div>

      <div className="guide-grid">
        <ExerciseImage media={shown.media} alt={`Minh họa bài ${shown.name}`} />
        <div className="stack" style={{ gap: 10 }}>
          {shown.steps.length > 0
            ? <ol className="steps">{shown.steps.map((st, i) => <li key={i}>{st}</li>)}</ol>
            : shown.instructions && <p>{shown.instructions}</p>}
          {shown.tips && <p className="notice"><b>Lưu ý:</b> {shown.tips}</p>}
        </div>
      </div>
    </div>
  );
}
