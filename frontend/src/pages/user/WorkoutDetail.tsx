import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { feedbackApi, profileApi, workoutApi } from '../../api/endpoints';
import { orNull, parseError } from '../../api/errors';
import type { Workout } from '../../api/types';
import Rating from '../../components/Rating';
import Toggle from '../../components/Toggle';
import { INTENSITIES, MISSED_REASONS, STATUS_LABEL, labelOf } from '../../lib/labels';
import { formatDateTime } from '../../lib/dates';
import { estimateKcal, metFor } from '../../lib/calories';

export default function WorkoutDetail() {
  const { id } = useParams();
  const workoutId = Number(id);
  const [workout, setWorkout] = useState<Workout | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [weightKg, setWeightKg] = useState<number | null>(null);

  useEffect(() => {
    orNull(profileApi.get()).then((p) => setWeightKg(p ? p.weightKg : null)).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!Number.isFinite(workoutId)) { setError('Đường dẫn buổi tập không hợp lệ'); return; }
    workoutApi.get(workoutId).then(setWorkout).catch((e) => setError(parseError(e).message));
  }, [workoutId]);

  async function finish(kind: 'complete' | 'abandon') {
    setError(''); setBusy(true);
    try {
      setWorkout(await (kind === 'complete' ? workoutApi.complete(workoutId) : workoutApi.abandon(workoutId)));
    } catch (e) {
      setError(parseError(e).message);
    } finally {
      setBusy(false);
    }
  }

  if (!workout) {
    return error
      ? <div className="stack"><p className="notice notice-error" role="alert">{error}</p><Link to="/workouts">Về danh sách buổi tập</Link></div>
      : <p className="muted">Đang tải...</p>;
  }

  const totalMin = workout.exercises.reduce((s, x) => s + x.plannedMin, 0);
  const totalKcal = weightKg
    ? workout.exercises.reduce((s, x) => s + estimateKcal(metFor(x.met, x.intensity), weightKg, x.plannedMin), 0)
    : null;

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Buổi tập #{workout.id}</h1>
        <p className="muted">
          <span className={`badge ${workout.status === 'COMPLETED' ? 'badge-ok' : workout.status === 'STARTED' ? 'badge-warn' : ''}`}>
            {STATUS_LABEL[workout.status]}
          </span>{' '}
          Bắt đầu {formatDateTime(workout.startedAt)} · Dự kiến {totalMin} phút{totalKcal !== null ? ` · ước tính ${totalKcal} kcal` : ''}
        </p>
      </header>

      {error && <p className="notice notice-error" role="alert">{error}</p>}

      <section className="panel">
        <ul className="list">
          {workout.exercises.map((x) => (
            <li key={x.exerciseId}>
              <div className="row-between">
                <b>{x.orderNo}. {x.name}</b>
                <span className="muted small">{x.plannedMin} phút · {labelOf(INTENSITIES, x.intensity)}</span>
              </div>
              {x.instructions && <p className="small muted" style={{ marginTop: 4 }}>{x.instructions}</p>}
            </li>
          ))}
        </ul>
      </section>

      {workout.status === 'STARTED' ? (
        <div className="row">
          <Link className="btn btn-primary" to={`/workouts/${workout.id}/play`}>Bắt đầu tập</Link>
          <button className="btn" disabled={busy} onClick={() => finish('complete')}>Đánh dấu đã hoàn thành</button>
          <button className="btn btn-danger" disabled={busy} onClick={() => finish('abandon')}>Huỷ buổi tập</button>
        </div>
      ) : (
        <FeedbackForm workout={workout} />
      )}
    </div>
  );
}

function FeedbackForm({ workout }: { workout: Workout }) {
  const completed = workout.status === 'COMPLETED';
  const [difficulty, setDifficulty] = useState<number | null>(null);
  const [fatigue, setFatigue] = useState<number | null>(null);
  const [satisfaction, setSatisfaction] = useState<number | null>(null);
  const [liked, setLiked] = useState(true);
  const [reason, setReason] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [done, setDone] = useState<'sent' | 'exists' | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    const missing: Record<string, string> = {};
    if (completed) {
      if (difficulty === null) missing.difficulty = 'Chọn một mức';
      if (fatigue === null) missing.fatigue = 'Chọn một mức';
      if (satisfaction === null) missing.satisfaction = 'Chọn một mức';
    } else if (!reason) {
      missing.notCompletedReason = 'Chọn một lý do';
    }
    setFields(missing);
    if (Object.keys(missing).length > 0) return;

    setBusy(true);
    try {
      await feedbackApi.create(
        completed
          ? { sessionId: workout.id, completed: true, difficulty: difficulty!, fatigue: fatigue!, satisfaction: satisfaction!, liked }
          : { sessionId: workout.id, completed: false, notCompletedReason: reason },
      );
      setDone('sent');
    } catch (err) {
      const info = parseError(err);
      if (info.status === 409) setDone('exists');
      else { setError(info.message); setFields(info.fields); }
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <p className="notice notice-ok" role="status">
        {done === 'sent' ? 'Đã ghi nhận phản hồi của bạn.' : 'Buổi tập này đã có phản hồi.'}{' '}
        <Link to="/">Về tổng quan</Link>
      </p>
    );
  }

  return (
    <form className="panel stack-lg" onSubmit={submit}>
      <div>
        <h2>{completed ? 'Buổi tập vừa rồi thế nào?' : 'Điều gì khiến bạn dừng lại?'}</h2>
        <p className="muted small">Phản hồi giúp hệ thống hiểu mức độ phù hợp của bài tập với bạn.</p>
      </div>
      {error && <p className="notice notice-error" role="alert">{error}</p>}

      {completed ? (
        <>
          <div className="form-grid">
            <Rating label="Độ khó" low="Dễ" high="Rất khó" value={difficulty} onChange={setDifficulty} error={fields.difficulty} />
            <Rating label="Mức mệt sau buổi tập" low="Không mệt" high="Rất mệt" value={fatigue} onChange={setFatigue} error={fields.fatigue} />
            <Rating label="Mức hài lòng" low="Không hài lòng" high="Rất hài lòng" value={satisfaction} onChange={setSatisfaction} error={fields.satisfaction} />
          </div>
          <Toggle label="Bạn có thích buổi tập này không?" value={liked} onChange={setLiked} yes="Có" no="Không" />
        </>
      ) : (
        <div className="field">
          <label htmlFor="reason">Lý do</label>
          <select id="reason" className="input" value={reason} onChange={(e) => setReason(e.target.value)}>
            <option value="">Chọn lý do</option>
            {MISSED_REASONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          {fields.notCompletedReason && <p className="err">{fields.notCompletedReason}</p>}
        </div>
      )}

      <div><button className="btn btn-primary" disabled={busy}>{busy ? 'Đang gửi...' : 'Gửi phản hồi'}</button></div>
    </form>
  );
}
