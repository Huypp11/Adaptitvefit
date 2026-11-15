import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { exerciseApi, feedbackApi, profileApi, recommendationApi, recommendationDataApi, workoutApi } from '../../api/endpoints';
import { orNull, orStatus, parseError } from '../../api/errors';
import type {
  Exercise, Recommendation, RecommendationFeedback, RecommendationFeedbackInput,
  RecommendationFeedbackStatus, RecommendationState,
} from '../../api/types';
import ExerciseImage from '../../components/ExerciseImage';
import Rating from '../../components/Rating';
import RiskBadge from '../../components/RiskBadge';
import {
  BREAKDOWN_LABEL, RECOMMENDATION_REASONS, RECOMMENDATION_STATUSES, STRATEGY_LABEL,
  modernReason, prettyReason,
} from '../../lib/labels';
import { estimateKcal, metFor } from '../../lib/calories';

/** Cac dong "dieu kien da kiem tra": bai nao qua loc cung thoa, nen khong phai ly do rieng cua bai do. */
const CHECKED_PREFIXES = [
  'Đúng địa điểm', 'Bài tập linh hoạt địa điểm', 'Không cần dụng cụ', 'Có đủ dụng cụ',
  'Cường độ ', 'Thời lượng ', 'Hợp nơi tập và sở thích', 'Vừa với ',
];
const isCheckedCondition = (reason: string) => CHECKED_PREFIXES.some((p) => reason.startsWith(p));

function streakText(state: RecommendationState): string {
  if (state.exercisedToday) return 'hôm nay đã tập';
  if (state.missedDays <= 1) return 'hôm nay chưa tập';
  return `chưa tập ${state.missedDays} ngày liên tiếp (tính cả hôm nay)`;
}

function clockOf(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

export default function Today() {
  const navigate = useNavigate();
  const [rec, setRec] = useState<Recommendation | null>(null);
  const [blocked, setBlocked] = useState<'checkin' | 'profile' | null>(null);
  const [library, setLibrary] = useState<Map<number, Exercise>>(new Map());
  const [weightKg, setWeightKg] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [conflict, setConflict] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [trackingEnabled, setTrackingEnabled] = useState(false);
  const [trackingError, setTrackingError] = useState('');
  const [skipped, setSkipped] = useState(false);
  const [skipTracked, setSkipTracked] = useState(false);

  useEffect(() => {
    Promise.all([
      orStatus(recommendationApi.today(), [400, 404]),
      exerciseApi.list({ size: 100 }),
      orNull(profileApi.get()),
    ])
      .then(([r, lib, profile]) => {
        if (r.status === 404) setBlocked('checkin');
        else if (r.status === 400) setBlocked('profile');
        else setRec(r.data);
        setLibrary(new Map(lib.content.map((e) => [e.id, e])));
        setWeightKg(profile ? profile.weightKg : null);
      })
      .catch((e) => setError(parseError(e).message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    recommendationDataApi.consent().then((consent) => setTrackingEnabled(consent.trackingEnabled))
      .catch((e) => setTrackingError(parseError(e).message));
  }, []);

  useEffect(() => {
    if (!rec || !trackingEnabled) return;
    const recordWhenVisible = () => {
      if (document.visibilityState !== 'visible') return;
      recommendationDataApi.recordEvent({
        eventType: 'DISPLAYED',
        recommendationId: rec.recommendationId,
        trackingId: rec.trackingId,
        idempotencyKey: `displayed:${rec.trackingId}`,
        recommendationMode: rec.mode,
      }).catch((e) => setTrackingError(parseError(e).message));
    };
    document.addEventListener('visibilitychange', recordWhenVisible);
    recordWhenVisible();
    return () => document.removeEventListener('visibilitychange', recordWhenVisible);
  }, [rec, trackingEnabled]);

  async function skipRecommendation() {
    if (!rec) return;
    setTrackingError('');
    try {
      if (trackingEnabled) {
        const result = await recommendationDataApi.recordEvent({
          eventType: 'SKIPPED',
          recommendationId: rec.recommendationId,
          trackingId: rec.trackingId,
          idempotencyKey: `skipped:${rec.trackingId}`,
          recommendationMode: rec.mode,
        });
        setSkipTracked(result.recorded);
      } else {
        setSkipTracked(false);
      }
      setSkipped(true);
    } catch (e) {
      setTrackingError(parseError(e).message);
    }
  }

  async function start(ids: number[], minutes: number[]) {
    setError(''); setConflict(false); setBusy(true);
    try {
      const w = await workoutApi.start(ids, {
        plannedMinutes: minutes,
        followRecommendation: rec?.mode !== 'PROFILE_FALLBACK',
        ...(rec?.trackingId ? { trackingId: rec.trackingId } : {}),
        ...(rec?.mode === 'CHECKIN' && rec.recommendationId !== null
          ? { recommendationId: rec.recommendationId } : {}),
      });
      navigate(`/workouts/${w.id}/play`);
    } catch (e) {
      const info = parseError(e);
      setError(info.message);
      setConflict(info.status === 409);
      setBusy(false);
    }
  }

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error && !rec && !blocked) return <p className="notice notice-error" role="alert">{error}</p>;

  if (blocked) {
    const needsCheckin = blocked === 'checkin';
    return (
      <div className="stack-lg">
        <header className="page-head"><h1>Gợi ý hôm nay</h1></header>
        <section className="empty-state panel">
          <div className="empty-state-icon" aria-hidden="true">
            {needsCheckin ? (
              <svg viewBox="0 0 48 48" fill="none">
                <rect x="7" y="11" width="34" height="30" rx="5" />
                <path d="M15 7v8M33 7v8M7 20h34M16 28h5m6 0h5m-16 7h5" />
                <path d="m27 34 3 3 6-7" />
              </svg>
            ) : (
              <svg viewBox="0 0 48 48" fill="none">
                <circle cx="24" cy="24" r="17" />
                <path d="M24 15v10l7 4M17 5l-5 5m19-5 5 5" />
              </svg>
            )}
          </div>
          <p className="eyebrow">{needsCheckin ? 'Bắt đầu từ hôm nay' : 'Chỉ còn một bước'}</p>
          <h2>{needsCheckin ? 'Check-in để nhận gợi ý phù hợp' : 'Hoàn thiện hồ sơ tập luyện'}</h2>
          <p className="muted">
            {needsCheckin
              ? 'Chia sẻ tình trạng hôm nay để hệ thống điều chỉnh chiến lược và bài tập theo năng lượng, thời gian của bạn.'
              : 'Cho biết mục tiêu, nơi tập và dụng cụ bạn có để gợi ý an toàn, phù hợp hơn.'}
          </p>
          <ol className="empty-state-steps">
            {needsCheckin ? (
              <>
                <li>Chọn nhanh cảm nhận và thời gian rảnh</li>
                <li>Xem chiến lược cùng bài tập được đề xuất</li>
              </>
            ) : (
              <>
                <li>Chọn mục tiêu và mức cường độ phù hợp</li>
                <li>Quay lại check-in để xem gợi ý hôm nay</li>
              </>
            )}
          </ol>
          <Link className="btn btn-primary" to={needsCheckin ? '/checkin' : '/profile'}>
            {needsCheckin ? 'Check-in ngay' : 'Hoàn thiện hồ sơ'}
          </Link>
        </section>
      </div>
    );
  }
  if (!rec) return null;

  const { strategy, state, recommendation: r } = rec;
  const done = strategy.code === 'DONE';
  // Mot nguon su that cho thoi luong: neu bai nam trong ke hoach thi dung so phut cua ke hoach.
  const planMinutes = new Map(r.plan.map((p) => [p.exerciseId, p.plannedMin]));
  const minutesOf = (id: number, fallback: number) => planMinutes.get(id) ?? fallback;
  const totalMinutes = r.plan.reduce((s, p) => s + p.plannedMin, 0);
  const planKcal = weightKg
    ? r.plan.reduce((sum, p) => {
        const ex = library.get(p.exerciseId);
        return sum + (ex ? estimateKcal(metFor(ex.met, ex.intensity), weightKg, p.plannedMin) : 0);
      }, 0)
    : null;
  const created = clockOf(rec.createdAt);
  const strategyReasons = strategy.reasons.map(modernReason);

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Gợi ý hôm nay</h1>
        <p><span className={`strategy ${strategy.code}`}>{STRATEGY_LABEL[strategy.code] ?? strategy.code}</span></p>
        {done ? (
          <p>
            Hôm nay bạn đã tập{state?.exercisedMinutesToday ? ` ${state.exercisedMinutesToday} phút` : ''}. Làm tốt lắm, bạn không cần tập thêm.
          </p>
        ) : (
          <p className="muted">{strategy.description}{strategyReasons[0] ? ` · ${strategyReasons[0]}` : ''}</p>
        )}
        {created && rec.mode === 'CHECKIN' && <p className="muted small">Gợi ý được tạo lúc {created} dựa trên check-in hôm nay.</p>}
      </header>

      {rec.mode === 'PROFILE_FALLBACK' && (
        <section className="notice notice-warn row-between" aria-label="Gợi ý mặc định">
          <span>Bài tập mặc định dựa trên hồ sơ. Check-in 15s để AI tối ưu theo lượng ngủ và mức stress hôm nay.</span>
          <Link className="btn btn-primary" to="/checkin">Check-in ngay</Link>
        </section>
      )}
      {rec.risk && (
        <p><RiskBadge score={rec.risk.riskScore} level={rec.risk.riskLevel} /></p>
      )}

      {error && (
        <p className="notice notice-error" role="alert">
          {error} {conflict && <Link to="/workouts">Xem buổi tập của tôi</Link>}
        </p>
      )}
      {trackingError && <p className="notice notice-error" role="alert">Không ghi được dữ liệu tương tác: {trackingError}</p>}
      {skipped && <p className="notice">
        {skipTracked ? 'Đã ghi nhận lựa chọn bỏ qua gợi ý hôm nay.' : 'Bạn đã bỏ qua gợi ý hôm nay; sự kiện không được lưu do bạn chưa đồng ý thu thập dữ liệu.'}
      </p>}
      {rec.interactionStatus !== 'GENERATED' && (
        <p className="notice">
          {rec.interactionStatus === 'STARTED' ? 'Bạn đã bắt đầu buổi tập từ gợi ý này.'
            : rec.interactionStatus === 'COMPLETED' ? 'Buổi tập từ gợi ý này đã hoàn thành.'
              : rec.interactionStatus === 'INVALID_WORKOUT' ? 'Buổi tập chưa đạt thời lượng tối thiểu và không được tính là hoàn thành.'
              : 'Buổi tập từ gợi ý này đã được dừng.'}
        </p>
      )}

      {r.items.length === 0 ? (
        <p className={done ? 'notice' : 'notice notice-warn'}>{done ? 'Bạn đã tập xong hôm nay, không có gợi ý thêm.' : (r.message ?? 'Chưa có bài tập phù hợp hôm nay.')}</p>
      ) : (
        <>
          <section className="panel stack">
            <h2>{done ? 'Thư giãn nhẹ (tuỳ chọn)' : 'Buổi tập hôm nay'}</h2>
            <p className="muted small">
              {r.plan.length} bài · {totalMinutes} phút trong quỹ {r.budgetMinutes} phút{planKcal !== null ? ` · ước tính khoảng ${planKcal} kcal` : ''}
            </p>
            <div>
              {r.plan.map((p) => (
                <div className="plan-row" key={p.exerciseId}><span>{p.name}</span><b>{p.plannedMin} phút</b></div>
              ))}
            </div>
            <div>
              <button className="btn btn-primary" disabled={busy || r.plan.length === 0}
                      onClick={() => start(r.plan.map((p) => p.exerciseId), r.plan.map((p) => p.plannedMin))}>
                {busy ? 'Đang bắt đầu...' : done ? 'Bắt đầu thư giãn' : 'Bắt đầu buổi tập này'}
              </button>
            </div>
          </section>

          <section className="stack">
            <h2>{done ? 'Một vài bài thư giãn' : 'Chi tiết từng bài'}</h2>
            <div className="grid-3">
              {r.items.map((it) => {
                const ex = library.get(it.exerciseId);
                const minutes = minutesOf(it.exerciseId, it.plannedMin);
                const reasons = it.reasons.map(prettyReason);
                const specific = reasons.filter((x) => !isCheckedCondition(x));
                const checked = reasons.filter(isCheckedCondition);
                return (
                  <article key={it.exerciseId} className="ex-card">
                    <ExerciseImage media={ex?.media ?? []} alt={`Minh họa bài ${it.name}`} />
                    <h3>{it.name}</h3>
                    <p className="small muted">{minutes} phút{planMinutes.has(it.exerciseId) ? '' : ' · bài thay thế, không nằm trong buổi tập trên'}</p>
                    <ul className="small" style={{ margin: 0, paddingLeft: '1.1rem' }}>
                      {(specific.length > 0 ? specific : ['Được xếp hạng cao trong các bài còn phù hợp']).slice(0, 3)
                        .map((x) => <li key={x}>{x}</li>)}
                    </ul>
                    <details>
                      <summary>Vì sao bài này phù hợp</summary>
                      <p className="small muted" style={{ margin: '8px 0 4px' }}>Điều kiện đã kiểm tra:</p>
                      <ul className="small" style={{ margin: 0, paddingLeft: '1.1rem' }}>
                        {checked.map((x) => <li key={x}>{x}</li>)}
                      </ul>
                      <p className="small muted" style={{ margin: '10px 0 4px' }}>Điểm xếp hạng: {Math.round(it.score * 100)}/100 (dùng để so sánh giữa các bài hôm nay)</p>
                      <div className="bars" style={{ marginTop: 8 }}>
                        {Object.entries(it.breakdown).map(([k, v]) => (
                          <div className="bar" key={k}>
                            <span>{BREAKDOWN_LABEL[k] ?? k}</span>
                            <span className="track"><i style={{ width: `${Math.round(v * 100)}%` }} /></span>
                            <span>{Math.round(v * 100)}</span>
                          </div>
                        ))}
                      </div>
                    </details>
                    <div>
                      <button className="btn" disabled={busy} onClick={() => start([it.exerciseId], [minutes])}>Chỉ tập bài này</button>
                    </div>
                  </article>
                );
              })}
            </div>
          </section>
        </>
      )}

      {!done && !skipped && r.plan.length > 0 && (
        <button className="btn btn-quiet" onClick={skipRecommendation}>Bỏ qua gợi ý hôm nay</button>
      )}

      <section className="panel">
        <details className="fold">
          <summary>Vì sao hệ thống chọn chiến lược này</summary>
          <div className="stack">
            <ul className="list">{strategyReasons.map((x) => <li key={x}>{x}</li>)}</ul>
            {rec.mode === 'CHECKIN' && state ? (
              <>
                <p className="muted small">
                  Dựa trên: ngủ {state.sleepHours}h, căng thẳng {state.stress}/5, năng lượng {state.energy}/5, rảnh {state.freeTimeMin} phút,
                  {' '}{streakText(state)}, {state.workoutFrequency7d} ngày có tập trong 7 ngày.
                  {rec.risk?.source === 'model' && rec.risk.riskScore !== null ? (
                    <> Dự đoán nguy cơ bỏ lỡ buổi tập: <RiskBadge score={rec.risk.riskScore} />. <Link to="/risk">Xem giải thích</Link>.
                      {rec.risk.demoModel && ' Đây là mô hình DEMO, chỉ mang tính minh hoạ.'}</>
                  ) : rec.risk ? (
                    <> Mức nguy cơ tạm tính theo lịch sử: <RiskBadge level={rec.risk.riskLevel} />.</>
                  ) : null}
                </p>
                <p className="muted small">
                  Các bài đã được lọc theo nơi tập, dụng cụ, cường độ tối đa và quỹ thời gian {r.budgetMinutes} phút trong hồ sơ và check-in của bạn.
                  Gợi ý được lưu cố định cho check-in hôm nay; nếu đổi hồ sơ, thay đổi sẽ áp dụng từ lần check-in tiếp theo.
                </p>
              </>
            ) : (
              <p className="muted small">Bài tập được chọn theo hồ sơ và hoạt động gần đây; đây chưa phải gợi ý đã tối ưu bằng dữ liệu check-in hôm nay.</p>
            )}
          </div>
        </details>
      </section>

      {rec.mode === 'CHECKIN' && rec.recommendationId !== null && <section className="panel">
        <details className="fold" open={Boolean(rec.feedback)}>
          <summary>{rec.feedback ? 'Phản hồi của bạn về gợi ý này' : 'Gợi ý chưa hợp, hoặc muốn góp ý?'}</summary>
          <RecommendationFeedbackForm
            key={rec.recommendationId}
            recommendationId={rec.recommendationId}
            feedback={rec.feedback}
            onSaved={(feedback) => setRec((current) => current ? { ...current, feedback } : current)}
          />
        </details>
      </section>}
      <p className="muted small">Gợi ý chỉ nhằm hỗ trợ duy trì thói quen vận động, không phải tư vấn y tế. Dừng ngay nếu bạn thấy đau.</p>
    </div>
  );
}

function RecommendationFeedbackForm({
  recommendationId, feedback, onSaved,
}: {
  recommendationId: number;
  feedback: RecommendationFeedback | null;
  onSaved: (feedback: RecommendationFeedback) => void;
}) {
  const [status, setStatus] = useState<RecommendationFeedbackStatus | ''>(feedback?.status ?? '');
  const [suitability, setSuitability] = useState<number | null>(feedback?.suitabilityRating ?? null);
  const [difficulty, setDifficulty] = useState<number | null>(feedback?.difficultyRating ?? null);
  const [reason, setReason] = useState(feedback?.reason ?? '');
  const [comment, setComment] = useState(feedback?.comment ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // "Khong phu hop" da noi len muc do phu hop; "chua thuc hien" thi khong co do kho de danh gia.
  const askSuitability = status !== '' && status !== 'NOT_SUITABLE';
  const askDifficulty = status === 'COMPLETED' || status === 'PARTIAL';
  const askReason = status === 'NOT_DONE' || status === 'NOT_SUITABLE';

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!status || busy) return;
    setError('');
    setBusy(true);
    const payload: RecommendationFeedbackInput = {
      status,
      ...(askSuitability && suitability !== null ? { suitabilityRating: suitability } : {}),
      ...(askDifficulty && difficulty !== null ? { difficultyRating: difficulty } : {}),
      ...(askReason && reason ? { reason } : {}),
      ...(comment.trim() ? { comment: comment.trim() } : {}),
    };
    try {
      const saved = feedback
        ? await feedbackApi.updateForRecommendation(recommendationId, payload)
        : await feedbackApi.createForRecommendation(recommendationId, payload);
      onSaved(saved);
    } catch (e) {
      setError(parseError(e).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <p className="muted small">Phản hồi là tùy chọn. Chỉ gửi khi bạn muốn chia sẻ; việc mở trang không được ghi nhận là đã xem.</p>
      {feedback && <p className="notice notice-ok" role="status">Đã ghi nhận phản hồi. Bạn có thể cập nhật lựa chọn bên dưới.</p>}
      {error && <p className="notice notice-error" role="alert">{error}</p>}

      <div className="field">
        <label htmlFor="recommendation-status">Tình trạng thực hiện</label>
        <select id="recommendation-status" className="input" value={status}
                onChange={(event) => setStatus(event.target.value as RecommendationFeedbackStatus | '')} required>
          <option value="">Chọn tình trạng</option>
          {RECOMMENDATION_STATUSES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>

      {(askSuitability || askDifficulty) && (
        <div className="form-grid">
          {askSuitability && (
            <Rating label="Mức độ phù hợp (tùy chọn)" low="Không hợp" high="Rất hợp" value={suitability} onChange={setSuitability} />
          )}
          {askDifficulty && (
            <Rating label="Độ khó (tùy chọn)" low="Quá dễ" high="Quá khó" value={difficulty} onChange={setDifficulty} />
          )}
        </div>
      )}

      {askReason && (
        <div className="field">
          <label htmlFor="recommendation-reason">Lý do (tùy chọn)</label>
          <select id="recommendation-reason" className="input" value={reason}
                  onChange={(event) => setReason(event.target.value)}>
            <option value="">Không muốn nêu lý do</option>
            {RECOMMENDATION_REASONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </div>
      )}

      <div className="field">
        <label htmlFor="recommendation-comment">Góp ý ngắn (tùy chọn)</label>
        <textarea id="recommendation-comment" className="input" maxLength={500} rows={3}
                  value={comment} onChange={(event) => setComment(event.target.value)} />
      </div>
      <div>
        <button className="btn btn-primary" disabled={busy || !status}>
          {busy ? 'Đang gửi...' : feedback ? 'Cập nhật phản hồi' : 'Gửi phản hồi'}
        </button>
      </div>
    </form>
  );
}
