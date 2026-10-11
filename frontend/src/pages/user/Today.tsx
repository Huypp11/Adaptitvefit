import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { exerciseApi, profileApi, recommendationApi, workoutApi } from '../../api/endpoints';
import { orNull, orStatus, parseError } from '../../api/errors';
import type { Exercise, Recommendation } from '../../api/types';
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
      const w = await workoutApi.start(ids, { plannedMinutes: minutes, followRecommendation: true });
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
        {strategy.description && <p className="muted">{strategy.description}</p>}
      </header>

      {error && (
        <p className="notice notice-error" role="alert">
          {error} {conflict && <Link to="/workouts">Xem buổi tập của tôi</Link>}
        </p>
      )}

      <section className="panel stack">
        <h2>Vì sao hệ thống chọn chiến lược này</h2>
        <ul className="list">{strategy.reasons.map((x) => <li key={x}>{x}</li>)}</ul>
        <p className="muted small">
          Dựa trên: ngủ {state.sleepHours}h, căng thẳng {state.stress}/5, năng lượng {state.energy}/5, rảnh {state.freeTimeMin} phút,
          chưa tập {state.missedDays} ngày liên tiếp, {state.workoutFrequency7d} ngày có tập trong 7 ngày.
          Mức nguy cơ <b>{RISK_LABEL[state.riskLevel] ?? state.riskLevel}</b> được tạm tính theo lịch sử tập luyện,
          chưa phải dự đoán của mô hình AI.
        </p>
      </section>

      {r.items.length === 0 ? (
        <p className="notice notice-warn">{r.message ?? 'Chưa có bài tập phù hợp hôm nay.'}</p>
      ) : (
        <>
          <section className="panel stack">
            <h2>Buổi tập gợi ý</h2>
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
                {busy ? 'Đang bắt đầu...' : 'Bắt đầu buổi tập này'}
              </button>
            </div>
          </section>

          <section className="stack">
            <h2>Các bài phù hợp nhất</h2>
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
      <p className="muted small">Gợi ý chỉ nhằm hỗ trợ duy trì thói quen vận động, không phải tư vấn y tế. Dừng ngay nếu bạn thấy đau.</p>
    </div>
  );
}
