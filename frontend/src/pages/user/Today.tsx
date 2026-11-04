import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { exerciseApi, feedbackApi, profileApi, recommendationApi, workoutApi } from '../../api/endpoints';
import { orNull, orStatus, parseError } from '../../api/errors';
import type {
  Exercise, Recommendation, RecommendationFeedback, RecommendationFeedbackInput,
  RecommendationFeedbackStatus,
} from '../../api/types';
import ExerciseImage from '../../components/ExerciseImage';
import { BREAKDOWN_LABEL, RISK_LABEL, STRATEGY_LABEL } from '../../lib/labels';
import { estimateKcal, metFor } from '../../lib/calories';

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

  const byId = useMemo(() => library, [library]);

  async function start(ids: number[], minutes: number[]) {
    setError(''); setConflict(false); setBusy(true);
    try {
      const w = await workoutApi.start(ids, {
        plannedMinutes: minutes, followRecommendation: true, recommendationId: rec?.recommendationId,
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
    return (
      <div className="stack-lg">
        <header className="page-head"><h1>Gợi ý hôm nay</h1></header>
        <p className="notice notice-warn">
          {blocked === 'checkin'
            ? <>Bạn chưa check-in hôm nay. <Link to="/checkin">Check-in ngay</Link> để nhận chiến lược và bài tập phù hợp.</>
            : <>Hãy <Link to="/profile">hoàn thiện hồ sơ</Link> (mục tiêu, nơi tập, dụng cụ) để nhận gợi ý.</>}
        </p>
      </div>
    );
  }
  if (!rec) return null;

  const { strategy, state, recommendation: r } = rec;
  const done = strategy.code === 'DONE';
  const planKcal = weightKg
    ? r.plan.reduce((sum, p) => {
        const ex = byId.get(p.exerciseId);
        return sum + (ex ? estimateKcal(metFor(ex.met, ex.intensity), weightKg, p.plannedMin) : 0);
      }, 0)
    : null;
  const planMinutes = r.plan.reduce((s, p) => s + p.plannedMin, 0);

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Gợi ý hôm nay</h1>
        <p><span className={`strategy ${strategy.code}`}>{STRATEGY_LABEL[strategy.code] ?? strategy.code}</span></p>
        {done && (
          <p>
            Hôm nay bạn đã tập{state.exercisedMinutesToday ? ` ${state.exercisedMinutesToday} phút` : ''}. Làm tốt lắm, bạn không cần tập thêm.
          </p>
        )}
        {strategy.description && !done && <p className="muted">{strategy.description}</p>}
      </header>

      {error && (
        <p className="notice notice-error" role="alert">
          {error} {conflict && <Link to="/workouts">Xem buổi tập của tôi</Link>}
        </p>
      )}
      {rec.interactionStatus !== 'GENERATED' && (
        <p className="notice">
          {rec.interactionStatus === 'STARTED' ? 'Bạn đã bắt đầu buổi tập từ gợi ý này.'
            : rec.interactionStatus === 'COMPLETED' ? 'Buổi tập từ gợi ý này đã hoàn thành.'
              : 'Buổi tập từ gợi ý này đã được dừng.'}
        </p>
      )}

      <section className="panel stack">
        <h2>Vì sao hệ thống chọn chiến lược này</h2>
        <ul className="list">{strategy.reasons.map((x) => <li key={x}>{x}</li>)}</ul>
        <p className="muted small">
          Dựa trên: ngủ {state.sleepHours}h, căng thẳng {state.stress}/5, năng lượng {state.energy}/5, rảnh {state.freeTimeMin} phút,
          chưa tập {state.missedDays} ngày liên tiếp, {state.workoutFrequency7d} ngày có tập trong 7 ngày.
          {rec.risk.source === 'model' && rec.risk.riskScore !== null ? (
            <> Mô hình AI dự đoán nguy cơ bỏ tập <b>{Math.round(rec.risk.riskScore * 100)}%</b> (mức {RISK_LABEL[state.riskLevel]?.toLowerCase() ?? state.riskLevel}). <Link to="/risk">Xem giải thích</Link>.
              {rec.risk.demoModel && ' Đây là mô hình DEMO, chỉ mang tính minh hoạ.'}</>
          ) : (
            <> Mức nguy cơ <b>{RISK_LABEL[state.riskLevel] ?? state.riskLevel}</b> đang được tạm tính theo lịch sử tập luyện vì dịch vụ AI chưa sẵn sàng.</>
          )}
        </p>
      </section>

      {r.items.length === 0 ? (
        <p className={done ? 'notice' : 'notice notice-warn'}>{done ? 'Bạn đã tập xong hôm nay, không có gợi ý thêm.' : (r.message ?? 'Chưa có bài tập phù hợp hôm nay.')}</p>
      ) : (
        <>
          <section className="panel stack">
            <h2>{done ? 'Thư giãn nhẹ (tuỳ chọn)' : 'Buổi tập gợi ý'}</h2>
            <p className="muted small">
              {r.plan.length} bài, {planMinutes} phút trong quỹ thời gian {r.budgetMinutes} phút
              {planKcal !== null ? `, ước tính khoảng ${planKcal} kcal` : ''}.
            </p>
            <ol className="steps">
              {r.plan.map((p) => <li key={p.exerciseId}>{p.name} ({p.plannedMin} phút)</li>)}
            </ol>
            <div>
              <button className="btn btn-primary" disabled={busy || r.plan.length === 0}
                      onClick={() => start(r.plan.map((p) => p.exerciseId), r.plan.map((p) => p.plannedMin))}>
                {busy ? 'Đang bắt đầu...' : done ? 'Bắt đầu thư giãn' : 'Bắt đầu buổi tập này'}
              </button>
            </div>
          </section>

          <section className="stack">
            <h2>{done ? 'Một vài bài thư giãn' : 'Các bài phù hợp nhất'}</h2>
            <div className="grid-3">
              {r.items.map((it) => {
                const ex = byId.get(it.exerciseId);
                return (
                  <article key={it.exerciseId} className="ex-card">
                    <ExerciseImage media={ex?.media ?? []} alt={`Minh họa bài ${it.name}`} />
                    <h3>{it.name}</h3>
                    <p className="small muted">{it.plannedMin} phút · điểm phù hợp {Math.round(it.score * 100)}/100</p>
                    <ul className="small" style={{ margin: 0, paddingLeft: '1.1rem' }}>
                      {it.reasons.map((x) => <li key={x}>{x}</li>)}
                    </ul>
                    <details>
                      <summary>Cách tính điểm</summary>
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
                      <button className="btn" disabled={busy} onClick={() => start([it.exerciseId], [it.plannedMin])}>Chỉ tập bài này</button>
                    </div>
                  </article>
                );
              })}
            </div>
          </section>
        </>
      )}
      <RecommendationFeedbackForm
        key={rec.recommendationId}
        recommendationId={rec.recommendationId}
        feedback={rec.feedback}
        onSaved={(feedback) => setRec((current) => current ? { ...current, feedback } : current)}
      />
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
  const [suitability, setSuitability] = useState(feedback?.suitabilityRating?.toString() ?? '');
  const [difficulty, setDifficulty] = useState(feedback?.difficultyRating?.toString() ?? '');
  const [reason, setReason] = useState(feedback?.reason ?? '');
  const [comment, setComment] = useState(feedback?.comment ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!status || busy) return;
    setError('');
    setBusy(true);
    const payload: RecommendationFeedbackInput = {
      status,
      ...(suitability ? { suitabilityRating: Number(suitability) } : {}),
      ...(difficulty ? { difficultyRating: Number(difficulty) } : {}),
      ...(reason ? { reason } : {}),
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
    <form className="panel stack" onSubmit={submit}>
      <div>
        <h2>{feedback ? 'Cập nhật phản hồi' : 'Gợi ý này có phù hợp với bạn không?'}</h2>
        <p className="muted small">Phản hồi là tùy chọn. Chỉ gửi khi bạn muốn chia sẻ; việc mở trang không được ghi nhận là đã xem.</p>
      </div>
      {feedback && <p className="notice notice-ok" role="status">Đã ghi nhận phản hồi. Bạn có thể cập nhật lựa chọn bên dưới.</p>}
      {error && <p className="notice notice-error" role="alert">{error}</p>}

      <div className="field">
        <label htmlFor="recommendation-status">Tình trạng thực hiện</label>
        <select id="recommendation-status" className="input" value={status}
                onChange={(event) => setStatus(event.target.value as RecommendationFeedbackStatus | '')} required>
          <option value="">Chọn tình trạng</option>
          <option value="COMPLETED">Đã hoàn thành gợi ý</option>
          <option value="PARTIAL">Đã thực hiện một phần</option>
          <option value="NOT_DONE">Chưa thực hiện</option>
          <option value="NOT_SUITABLE">Gợi ý không phù hợp</option>
        </select>
      </div>

      <div className="form-grid">
        <div className="field">
          <label htmlFor="recommendation-suitability">Mức độ phù hợp (tùy chọn)</label>
          <select id="recommendation-suitability" className="input" value={suitability}
                  onChange={(event) => setSuitability(event.target.value)}>
            <option value="">Bỏ qua</option>
            <option value="1">1 - Không phù hợp</option>
            <option value="2">2 - Ít phù hợp</option>
            <option value="3">3 - Tạm được</option>
            <option value="4">4 - Phù hợp</option>
            <option value="5">5 - Rất phù hợp</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="recommendation-difficulty">Độ khó (tùy chọn)</label>
          <select id="recommendation-difficulty" className="input" value={difficulty}
                  onChange={(event) => setDifficulty(event.target.value)}>
            <option value="">Bỏ qua</option>
            <option value="1">1 - Quá dễ</option>
            <option value="2">2 - Hơi dễ</option>
            <option value="3">3 - Phù hợp</option>
            <option value="4">4 - Hơi khó</option>
            <option value="5">5 - Quá khó</option>
          </select>
        </div>
      </div>

      {(status === 'NOT_DONE' || status === 'NOT_SUITABLE') && (
        <div className="field">
          <label htmlFor="recommendation-reason">Lý do (tùy chọn)</label>
          <select id="recommendation-reason" className="input" value={reason}
                  onChange={(event) => setReason(event.target.value)}>
            <option value="">Không muốn nêu lý do</option>
            <option value="NO_TIME">Thiếu thời gian</option>
            <option value="TOO_TIRED">Mệt mỏi</option>
            <option value="NO_EQUIPMENT">Không có dụng cụ</option>
            <option value="NOT_SUITABLE">Bài tập không phù hợp</option>
            <option value="OTHER">Lý do khác</option>
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
