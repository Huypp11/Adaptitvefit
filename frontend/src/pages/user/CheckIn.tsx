import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { checkinApi, workoutApi } from '../../api/endpoints';
import { orNull, parseError } from '../../api/errors';
import type { Checkin, CheckinInput } from '../../api/types';
import Field from '../../components/Field';
import RangeField from '../../components/RangeField';
import Rating from '../../components/Rating';
import Segmented from '../../components/Segmented';
import { datePart, toISODate } from '../../lib/dates';
import { MISSED_REASONS, labelOf } from '../../lib/labels';

type TrainingMode = 'DONE' | 'LATER' | 'SKIP';

const TRAINING_MODES: { value: TrainingMode; label: string }[] = [
  { value: 'DONE', label: 'Đã tập rồi' },
  { value: 'LATER', label: 'Chưa tập, hôm nay sẽ tập' },
  { value: 'SKIP', label: 'Hôm nay mình không tập' },
];

export default function CheckIn() {
  const navigate = useNavigate();
  const [existing, setExisting] = useState<Checkin | null>(null);
  const [completedWorkoutToday, setCompletedWorkoutToday] = useState(false);
  const [completedWorkoutDuration, setCompletedWorkoutDuration] = useState(1);
  const [loading, setLoading] = useState(true);

  const [sleep, setSleep] = useState<number | null>(null);
  const [stress, setStress] = useState<number | null>(null);
  const [energy, setEnergy] = useState<number | null>(null);
  const [mood, setMood] = useState<number | null>(null);
  const [motivation, setMotivation] = useState<number | null>(null);
  const [freeTime, setFreeTime] = useState<number | null>(null);
  const [mode, setMode] = useState<TrainingMode>('LATER');
  const [duration, setDuration] = useState<number | null>(null);
  const [difficulty, setDifficulty] = useState<number | null>(null);
  const [reason, setReason] = useState('');

  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    Promise.all([orNull(checkinApi.today()), workoutApi.history(0, 100)])
      .then(([today, history]) => {
        setExisting(today);
        const todayDate = toISODate(new Date());
        const completedToday = history.content.filter(
          (workout) => workout.status === 'COMPLETED'
            && workout.finishedAt !== null
            && datePart(workout.finishedAt) === todayDate,
        );
        setCompletedWorkoutToday(completedToday.length > 0);
        if (completedToday.length > 0) {
          const minutes = completedToday.reduce((total, workout) => {
            if (!workout.finishedAt) return total;
            return total + Math.max(1, Math.round(
              (new Date(workout.finishedAt).getTime() - new Date(workout.startedAt).getTime()) / 60_000,
            ));
          }, 0);
          setCompletedWorkoutDuration(Math.min(300, Math.max(1, minutes)));
        }
      })
      .catch((e) => setError(parseError(e).message))
      .finally(() => setLoading(false));
  }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    const missing: Record<string, string> = {};
    if (stress === null) missing.stress = 'Chọn một mức';
    if (energy === null) missing.energy = 'Chọn một mức';
    if (mood === null) missing.mood = 'Chọn một mức';
    if (motivation === null) missing.motivation = 'Chọn một mức';
    if (sleep === null) missing.sleepHours = 'Chọn số giờ ngủ';
    if (freeTime === null) missing.freeTimeMin = 'Chọn thời gian rảnh';
    if (!completedWorkoutToday && mode === 'DONE' && duration === null) missing.durationMin = 'Chọn thời lượng buổi tập';
    // Co ly do = chu dong khong tap; khong co ly do = chua tap luc check-in (se tap sau).
    if (!completedWorkoutToday && mode === 'SKIP' && !reason) missing.missedReason = 'Chọn lý do để hệ thống điều chỉnh gợi ý';
    setFields(missing);
    if (Object.keys(missing).length > 0) return;

    const payload: CheckinInput = {
      sleepHours: sleep!, stress: stress!, energy: energy!, mood: mood!,
      motivation: motivation!, freeTimeMin: freeTime!,
      exercisedToday: completedWorkoutToday || mode === 'DONE',
    };
    if (completedWorkoutToday) {
      payload.durationMin = completedWorkoutDuration;
    } else if (mode === 'DONE') {
      payload.durationMin = duration!;
      if (difficulty !== null) payload.difficulty = difficulty;
    } else if (mode === 'SKIP') {
      payload.missedReason = reason;
    }

    setBusy(true);
    try {
      await checkinApi.create(payload);
      navigate('/', { replace: true });
    } catch (err) {
      const info = parseError(err);
      setError(info.message);
      setFields(info.fields);
      if (info.status === 409) checkinApi.today().then(setExisting).catch(() => undefined);
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <p className="muted">Đang tải...</p>;

  if (existing) {
    return (
      <div className="stack-lg">
        <header className="page-head">
          <h1>Bạn đã check-in hôm nay</h1>
          <p className="muted">Mỗi ngày chỉ check-in một lần. Hẹn bạn vào ngày mai.</p>
        </header>
        <section className="panel">
          <ul className="list">
            <li>Giấc ngủ: <b>{existing.sleepHours} giờ</b></li>
            <li>Căng thẳng <b>{existing.stress}/5</b> · Năng lượng <b>{existing.energy}/5</b> · Tâm trạng <b>{existing.mood}/5</b> · Động lực <b>{existing.motivation}/5</b></li>
            <li>Thời gian rảnh: <b>{existing.freeTimeMin} phút</b></li>
            <li>
              {existing.exercisedToday
                ? <>Đã tập <b>{existing.durationMin} phút</b>{existing.difficulty ? <>, độ khó <b>{existing.difficulty}/5</b></> : null}</>
                : existing.missedReason
                  ? <>Hôm nay không tập, lý do: <b>{labelOf(MISSED_REASONS, existing.missedReason)}</b></>
                  : <>Chưa tập lúc check-in. Buổi tập hoàn thành trong ngày sẽ được tính tự động.</>}
            </li>
          </ul>
        </section>
        <div className="row">
          <Link className="btn btn-primary" to="/today">{existing.exercisedToday ? 'Xem gợi ý thư giãn' : 'Xem gợi ý hôm nay'}</Link>
          <Link className="btn" to="/exercises">Thư viện bài tập</Link>
          <Link className="btn" to="/">Về tổng quan</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Hôm nay bạn thế nào?</h1>
        <p className="muted">Mất khoảng một phút. Trả lời theo cảm nhận thật của bạn.</p>
      </header>

      {error && <p className="notice notice-error" role="alert">{error}</p>}

      <form className="panel stack-lg" onSubmit={submit}>
        <div className="form-grid">
          <RangeField id="sleep" label="Đêm qua bạn ngủ bao nhiêu giờ?" value={sleep} min={0} max={14}
                      step={0.5} suffix="giờ" lowLabel="0 giờ" highLabel="14 giờ"
                      error={fields.sleepHours} onChange={setSleep} />
          <RangeField id="free" label="Hôm nay bạn rảnh bao nhiêu phút?" value={freeTime} min={0} max={480}
                      step={1} suffix="phút" lowLabel="0 phút" highLabel="480 phút"
                      error={fields.freeTimeMin} onChange={setFreeTime} />
        </div>

        <div className="form-grid">
          <Rating label="Mức căng thẳng" low="Thấp" high="Cao" value={stress} onChange={setStress} error={fields.stress} />
          <Rating label="Mức năng lượng" low="Thấp" high="Cao" value={energy} onChange={setEnergy} error={fields.energy} />
          <Rating label="Tâm trạng" low="Tệ" high="Rất tốt" value={mood} onChange={setMood} error={fields.mood} />
          <Rating label="Động lực tập luyện" low="Thấp" high="Cao" value={motivation} onChange={setMotivation} error={fields.motivation} />
        </div>

        {completedWorkoutToday ? (
          <p className="notice notice-ok" role="status">
            Đã tự ghi nhận buổi tập hoàn thành hôm nay ({completedWorkoutDuration} phút).
            Độ khó sẽ lấy từ phản hồi buổi tập, không hỏi lại ở check-in.
          </p>
        ) : (
          <>
            <Segmented label="Hôm nay bạn đã tập chưa?" value={mode} options={TRAINING_MODES} onChange={setMode} />
            {mode === 'DONE' && (
              <div className="form-grid">
                <RangeField id="dur" label="Bạn đã tập bao nhiêu phút?" value={duration} min={1} max={300}
                            step={1} suffix="phút" lowLabel="1 phút" highLabel="300 phút"
                            error={fields.durationMin} onChange={setDuration} />
                <Rating label="Độ khó buổi tập" low="Dễ" high="Rất khó" value={difficulty} onChange={setDifficulty} error={fields.difficulty} />
              </div>
            )}
            {mode === 'LATER' && (
              <p className="muted small">Hệ thống sẽ gợi ý buổi tập cho hôm nay. Bạn không cần nhập lại sau khi tập xong.</p>
            )}
            {mode === 'SKIP' && (
              <Field label="Vì sao hôm nay bạn không tập?" htmlFor="reason" error={fields.missedReason}>
                <select id="reason" className="input" value={reason} onChange={(e) => setReason(e.target.value)}>
                  <option value="">Chọn lý do</option>
                  {MISSED_REASONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
              </Field>
            )}
          </>
        )}

        <div><button className="btn btn-primary" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu check-in'}</button></div>
      </form>
    </div>
  );
}
