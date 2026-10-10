import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { checkinApi } from '../../api/endpoints';
import { orNull, parseError } from '../../api/errors';
import type { Checkin, CheckinInput } from '../../api/types';
import Field from '../../components/Field';
import Rating from '../../components/Rating';
import Toggle from '../../components/Toggle';
import { MISSED_REASONS, labelOf } from '../../lib/labels';

export default function CheckIn() {
  const navigate = useNavigate();
  const [existing, setExisting] = useState<Checkin | null>(null);
  const [loading, setLoading] = useState(true);

  const [sleep, setSleep] = useState('');
  const [stress, setStress] = useState<number | null>(null);
  const [energy, setEnergy] = useState<number | null>(null);
  const [mood, setMood] = useState<number | null>(null);
  const [motivation, setMotivation] = useState<number | null>(null);
  const [freeTime, setFreeTime] = useState('');
  const [exercised, setExercised] = useState(false);
  const [duration, setDuration] = useState('');
  const [difficulty, setDifficulty] = useState<number | null>(null);
  const [reason, setReason] = useState('');

  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    orNull(checkinApi.today())
      .then(setExisting)
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
    if (exercised && !duration) missing.durationMin = 'Nhập số phút bạn đã tập';
    setFields(missing);
    if (Object.keys(missing).length > 0) return;

    const payload: CheckinInput = {
      sleepHours: Number(sleep), stress: stress!, energy: energy!, mood: mood!,
      motivation: motivation!, freeTimeMin: Number(freeTime), exercisedToday: exercised,
    };
    if (exercised) {
      payload.durationMin = Number(duration);
      if (difficulty !== null) payload.difficulty = difficulty;
    } else if (reason) {
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
                : <>Chưa tập{existing.missedReason ? <>, lý do: <b>{labelOf(MISSED_REASONS, existing.missedReason)}</b></> : null}</>}
            </li>
          </ul>
        </section>
        <div className="row">
          <Link className="btn btn-primary" to="/exercises">Chọn bài tập</Link>
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
          <Field label="Đêm qua bạn ngủ bao nhiêu giờ?" htmlFor="sleep" error={fields.sleepHours}>
            <input id="sleep" className="input" type="number" step="0.5" min="0" max="14" required
                   value={sleep} onChange={(e) => setSleep(e.target.value)} />
          </Field>
          <Field label="Hôm nay bạn rảnh bao nhiêu phút?" htmlFor="free" error={fields.freeTimeMin}>
            <input id="free" className="input" type="number" min="0" max="480" required
                   value={freeTime} onChange={(e) => setFreeTime(e.target.value)} />
          </Field>
        </div>

        <div className="form-grid">
          <Rating label="Mức căng thẳng" low="Thấp" high="Cao" value={stress} onChange={setStress} error={fields.stress} />
          <Rating label="Mức năng lượng" low="Thấp" high="Cao" value={energy} onChange={setEnergy} error={fields.energy} />
          <Rating label="Tâm trạng" low="Tệ" high="Rất tốt" value={mood} onChange={setMood} error={fields.mood} />
          <Rating label="Động lực tập luyện" low="Thấp" high="Cao" value={motivation} onChange={setMotivation} error={fields.motivation} />
        </div>

        <Toggle label="Hôm nay bạn đã tập chưa?" value={exercised} onChange={setExercised} yes="Đã tập" no="Chưa tập" />

        {exercised ? (
          <div className="form-grid">
            <Field label="Bạn đã tập bao nhiêu phút?" htmlFor="dur" error={fields.durationMin}>
              <input id="dur" className="input" type="number" min="1" max="300"
                     value={duration} onChange={(e) => setDuration(e.target.value)} />
            </Field>
            <Rating label="Độ khó buổi tập" low="Dễ" high="Rất khó" value={difficulty} onChange={setDifficulty} error={fields.difficulty} />
          </div>
        ) : (
          <Field label="Lý do chưa tập (không bắt buộc)" htmlFor="reason" error={fields.missedReason}>
            <select id="reason" className="input" value={reason} onChange={(e) => setReason(e.target.value)}>
              <option value="">Không nêu</option>
              {MISSED_REASONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
        )}

        <div><button className="btn btn-primary" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu check-in'}</button></div>
      </form>
    </div>
  );
}
