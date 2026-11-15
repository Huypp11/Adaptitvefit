import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { checkinApi, feedbackApi, profileApi, recommendationApi, workoutApi } from '../../api/endpoints';
import { orNull, orStatus, parseError } from '../../api/errors';
import type {
  Checkin, Profile, Recommendation, RecommendationFeedbackStatus, WorkoutSummary,
} from '../../api/types';
import RiskBadge from '../../components/RiskBadge';
import { RECOMMENDATION_REASONS, STATUS_LABEL, STRATEGY_LABEL, modernReason } from '../../lib/labels';
import { addDays, datePart, formatDateLong, formatDateTime, greeting, toISODate, weekdayLabel } from '../../lib/dates';

interface Data {
  profile: Profile | null;
  checkins: Checkin[];
  workouts: WorkoutSummary[];
  rec: Recommendation | null;
  pending: Recommendation | null;   // goi y cua ngay truoc chua co phan hoi
}

export default function Dashboard() {
  const [data, setData] = useState<Data | null>(null);
  const [error, setError] = useState('');
  const now = useMemo(() => new Date(), []);

  useEffect(() => {
    const from = toISODate(addDays(now, -6));
    const todayKey = toISODate(now);
    Promise.all([orNull(profileApi.get()), checkinApi.list(from, todayKey), workoutApi.history(0, 50),
      orStatus(recommendationApi.today(), [400, 404]),
      // Loi khi doc lich su khong duoc lam hong ca trang tong quan
      recommendationApi.history(0, 5).catch(() => null)])
      .then(([profile, checkins, history, rec, recHistory]) => {
        const pending = recHistory?.content.find((r) =>
          datePart(r.createdAt) !== todayKey && r.feedback === null
          && r.strategy.code !== 'DONE' && r.recommendation.plan.length > 0
          && (r.interactionStatus === 'GENERATED' || r.interactionStatus === 'ABANDONED')) ?? null;
        setData({ profile, checkins, workouts: history.content, rec: rec.data, pending });
      })
      .catch((e) => setError(parseError(e).message));
  }, [now]);

  if (error) return <p className="notice notice-error" role="alert">{error}</p>;
  if (!data) return <p className="muted">Đang tải...</p>;

  const todayKey = toISODate(now);
  const todayCheckin = data.checkins.find((c) => c.checkinDate === todayKey);
  const checkinDays = new Set(data.checkins.map((c) => c.checkinDate));
  const workoutDays = new Set(
    data.workouts.filter((w) => w.status === 'COMPLETED' && w.finishedAt).map((w) => datePart(w.finishedAt!)),
  );
  const days = Array.from({ length: 7 }, (_, i) => addDays(now, i - 6));
  const active = data.workouts.find((w) => w.status === 'STARTED');

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>{greeting(now)}</h1>
        <p className="muted">{formatDateLong(now)}</p>
      </header>

      {!data.profile && (
        <p className="notice notice-warn">
          Bạn chưa có hồ sơ. <Link to="/profile">Điền hồ sơ</Link> để bắt đầu.
        </p>
      )}
      {active && (
        <p className="notice">
          Bạn đang có một buổi tập chưa kết thúc. <Link to={`/workouts/${active.id}`}>Tiếp tục buổi tập</Link>
        </p>
      )}

      {data.pending && <PendingFeedback rec={data.pending} onDone={() => setData({ ...data, pending: null })} />}

      <section className="panel stack">
        <h2>7 ngày gần đây</h2>
        <div className="week">
          {days.map((d) => {
            const key = toISODate(d);
            return (
              <div key={key} className={`day ${key === todayKey ? 'today' : ''}`}>
                <span className="wd">{weekdayLabel(d)}</span>
                <span className="dn">{d.getDate()}</span>
                <span className="marks">
                  <i className={`mark checkin ${checkinDays.has(key) ? 'on' : ''}`} title="Check-in" />
                  <i className={`mark workout ${workoutDays.has(key) ? 'on' : ''}`} title="Đã tập" />
                </span>
              </div>
            );
          })}
        </div>
        <p className="legend">
          <span><i className="mark checkin on" />Đã check-in</span>
          <span><i className="mark workout on" />Đã hoàn thành buổi tập</span>
        </p>
      </section>

      <section className="panel stack">
        <h2>Hôm nay</h2>
        {!todayCheckin ? (
          <>
            <p className="muted">
              {data.rec?.mode === 'PROFILE_FALLBACK'
                ? 'Bài tập mặc định dựa trên hồ sơ. Check-in 15s để AI tối ưu theo lượng ngủ và mức stress hôm nay.'
                : 'Bạn chưa check-in hôm nay. Check-in để nhận chiến lược và bài tập phù hợp.'}
            </p>
            {data.rec?.mode === 'PROFILE_FALLBACK' && (
              <>
                <p><span className={`strategy ${data.rec.strategy.code}`}>Gợi ý mặc định từ hồ sơ</span></p>
                <p className="small">{data.rec.recommendation.plan.length} bài · {data.rec.recommendation.plan.reduce((sum, p) => sum + p.plannedMin, 0)} phút</p>
              </>
            )}
            <div><Link className="btn btn-primary" to="/checkin">Check-in ngay</Link></div>
          </>
        ) : (
          <>
            <p className="small">
              Ngủ <b>{todayCheckin.sleepHours}h</b> · Căng thẳng <b>{todayCheckin.stress}/5</b> · Năng lượng <b>{todayCheckin.energy}/5</b>
              {todayCheckin.exercisedToday && <> · Đã tập{todayCheckin.durationMin ? ` ${todayCheckin.durationMin} phút` : ''}</>}
            </p>
            {data.rec ? (
              <>
                <p><span className={`strategy ${data.rec.strategy.code}`}>{STRATEGY_LABEL[data.rec.strategy.code] ?? data.rec.strategy.code}</span></p>
                <p className="muted small">{modernReason(data.rec.strategy.reasons[0] ?? '')}</p>
                {data.rec.risk?.source === 'model' && data.rec.risk.riskScore !== null && (
                  <p className="small">
                    Nguy cơ bỏ lỡ buổi tập: <RiskBadge score={data.rec.risk.riskScore} />
                    {data.rec.risk.demoModel ? ', mô hình DEMO' : ''}
                  </p>
                )}
                {data.rec.risk && data.rec.risk.source !== 'model' && (
                  <p className="small">
                    Mức nguy cơ tạm tính theo lịch sử: <RiskBadge level={data.rec.risk.riskLevel} />
                  </p>
                )}
                {data.rec.recommendation.plan.length > 0 && (
                  <p className="small">
                    {data.rec.recommendation.plan.length} bài · {data.rec.recommendation.plan.reduce((sum, p) => sum + p.plannedMin, 0)} phút
                  </p>
                )}
              </>
            ) : (
              <p className="muted">Hãy hoàn thiện hồ sơ để nhận gợi ý.</p>
            )}
            <div className="row">
              {active ? (
                <Link className="btn btn-primary" to={`/workouts/${active.id}`}>Tiếp tục buổi tập</Link>
              ) : data.rec ? (
                <Link className="btn btn-primary" to="/today">
                  {data.rec.strategy.code === 'DONE' ? 'Xem gợi ý thư giãn' : 'Xem buổi tập hôm nay'}
                </Link>
              ) : (
                <Link className="btn btn-primary" to="/profile">Hoàn thiện hồ sơ</Link>
              )}
              <Link className="btn btn-quiet" to="/exercises">Tự chọn bài</Link>
              <Link className="btn btn-quiet" to="/checkin">Xem check-in</Link>
            </div>
          </>
        )}
      </section>

      <section className="panel stack">
        <div className="row-between">
          <h2>Buổi tập gần đây</h2>
          <Link to="/workouts">Xem tất cả</Link>
        </div>
        {data.workouts.length === 0 ? (
          <p className="muted">Chưa có buổi tập nào. <Link to="/exercises">Chọn bài tập đầu tiên</Link>.</p>
        ) : (
          <ul className="list">
            {data.workouts.slice(0, 3).map((w) => (
              <li key={w.id} className="row-between">
                <Link to={`/workouts/${w.id}`}>{formatDateTime(w.startedAt)}</Link>
                <span className={`badge ${w.status === 'COMPLETED' ? 'badge-ok' : w.status === 'STARTED' ? 'badge-warn' : w.status === 'INVALID_WORKOUT' ? 'badge-off' : ''}`}>
                  {STATUS_LABEL[w.status]}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

const PENDING_CHOICES: { status: RecommendationFeedbackStatus; label: string; askReason: boolean }[] = [
  { status: 'COMPLETED', label: 'Đã tập', askReason: false },
  { status: 'PARTIAL', label: 'Tập một phần', askReason: false },
  { status: 'NOT_DONE', label: 'Chưa tập', askReason: true },
  { status: 'NOT_SUITABLE', label: 'Gợi ý không hợp', askReason: true },
];

/** Mot cau hoi duy nhat ve goi y gan nhat chua co phan hoi, hien o tong quan (tuy chon, co the bo qua). */
function PendingFeedback({ rec, onDone }: { rec: Recommendation; onDone: () => void }) {
  const [choice, setChoice] = useState<RecommendationFeedbackStatus | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [dismissed, setDismissed] = useState(false);
  const date = new Date(rec.createdAt).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
  const needsReason = PENDING_CHOICES.find((c) => c.status === choice)?.askReason ?? false;

  async function send(status: RecommendationFeedbackStatus, why?: string) {
    if (rec.recommendationId === null) {
      setError('Không thể gửi phản hồi cho gợi ý tạm thời.');
      return;
    }
    setBusy(true); setError('');
    try {
      await feedbackApi.createForRecommendation(rec.recommendationId, { status, ...(why ? { reason: why } : {}) });
      onDone();
    } catch (e) {
      setError(parseError(e).message);
      setBusy(false);
    }
  }

  function pick(c: typeof PENDING_CHOICES[number]) {
    if (c.askReason) setChoice(c.status);
    else void send(c.status);
  }

  function submitReason(e: FormEvent) {
    e.preventDefault();
    if (choice) void send(choice, reason || undefined);
  }

  if (dismissed) return null;
  return (
    <section className="panel stack" aria-label="Phản hồi gợi ý hôm trước">
      <h2>Gợi ý ngày {date} thế nào?</h2>
      <p className="muted small">
        Bạn có tập theo gợi ý đó không? ({rec.recommendation.plan.map((p) => p.name).join(', ')}). Trả lời giúp hệ thống gợi ý tốt hơn; bạn có thể bỏ qua.
      </p>
      {error && <p className="notice notice-error" role="alert">{error}</p>}
      {choice === null ? (
        <div className="row">
          {PENDING_CHOICES.map((c) => (
            <button key={c.status} type="button" className="btn" disabled={busy} onClick={() => pick(c)}>{c.label}</button>
          ))}
          <button type="button" className="btn btn-quiet" onClick={() => setDismissed(true)}>Để sau</button>
        </div>
      ) : (
        needsReason && (
          <form className="row" onSubmit={submitReason}>
            <select className="input" aria-label="Lý do" value={reason} onChange={(e) => setReason(e.target.value)}>
              <option value="">Không muốn nêu lý do</option>
              {RECOMMENDATION_REASONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
            <button className="btn btn-primary" disabled={busy}>{busy ? 'Đang gửi...' : 'Gửi'}</button>
            <button type="button" className="btn btn-quiet" disabled={busy} onClick={() => setChoice(null)}>Quay lại</button>
          </form>
        )
      )}
    </section>
  );
}
