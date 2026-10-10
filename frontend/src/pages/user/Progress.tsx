import { useEffect, useMemo, useState } from 'react';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { checkinApi, workoutApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import type { Checkin, WorkoutSummary } from '../../api/types';
import { addDays, toISODate } from '../../lib/dates';
import { computeWorkoutStats } from '../../lib/stats';

export default function Progress() {
  const [checkins, setCheckins] = useState<Checkin[] | null>(null);
  const [workouts, setWorkouts] = useState<WorkoutSummary[] | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const now = new Date();
    Promise.all([checkinApi.list(toISODate(addDays(now, -29)), toISODate(now)), workoutApi.history(0, 100)])
      .then(([c, w]) => { setCheckins(c); setWorkouts(w.content); })
      .catch((e) => setError(parseError(e).message));
  }, []);

  const stats = useMemo(() => (workouts ? computeWorkoutStats(workouts) : null), [workouts]);
  const series = useMemo(
    () => (checkins ? [...checkins].reverse().map((c) => ({ ...c, label: c.checkinDate.slice(5) })) : []),
    [checkins],
  );

  if (error) return <p className="notice notice-error" role="alert">{error}</p>;
  if (!checkins || !stats) return <p className="muted">Đang tải...</p>;

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Tiến độ</h1>
        <p className="muted">Tổng hợp từ check-in 30 ngày và các buổi tập gần nhất của bạn.</p>
      </header>

      <section className="grid-3">
        <div className="panel stat"><b>{stats.completedLast7Days}</b><span className="muted">buổi hoàn thành trong 7 ngày</span></div>
        <div className="panel stat">
          <b>{stats.completionRate === null ? '-' : `${stats.completionRate}%`}</b>
          <span className="muted">tỉ lệ hoàn thành ({stats.completed} xong, {stats.abandoned} huỷ)</span>
        </div>
        <div className="panel stat"><b>{stats.streak}</b><span className="muted">ngày liên tiếp có buổi tập</span></div>
      </section>
      <p className="muted small">Tổng thời gian đã tập: {stats.totalMinutes} phút (tính từ lúc bắt đầu đến lúc kết thúc buổi tập).</p>

      {series.length === 0 ? (
        <p className="panel muted">Chưa có dữ liệu check-in. Hãy check-in vài ngày để thấy xu hướng.</p>
      ) : (
        <>
          <section className="panel stack">
            <h2>Giấc ngủ (giờ)</h2>
            <div style={{ height: 240 }}>
              <ResponsiveContainer>
                <LineChart data={series}>
                  <CartesianGrid stroke="#d5deda" strokeDasharray="3 3" />
                  <XAxis dataKey="label" /><YAxis domain={[0, 12]} /><Tooltip />
                  <Line type="monotone" dataKey="sleepHours" name="Giờ ngủ" stroke="#0f766e" strokeWidth={2} dot />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </section>
          <section className="panel stack">
            <h2>Căng thẳng, năng lượng và động lực (1-5)</h2>
            <div style={{ height: 260 }}>
              <ResponsiveContainer>
                <LineChart data={series}>
                  <CartesianGrid stroke="#d5deda" strokeDasharray="3 3" />
                  <XAxis dataKey="label" /><YAxis domain={[1, 5]} allowDecimals={false} /><Tooltip /><Legend />
                  <Line type="monotone" dataKey="stress" name="Căng thẳng" stroke="#b42318" strokeWidth={2} />
                  <Line type="monotone" dataKey="energy" name="Năng lượng" stroke="#0f766e" strokeWidth={2} />
                  <Line type="monotone" dataKey="motivation" name="Động lực" stroke="#c98a00" strokeWidth={2} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </section>
        </>
      )}
      <p className="muted small">Các số liệu này chỉ mô tả hoạt động của bạn, không phải đánh giá y tế và không nói lên nguyên nhân.</p>
    </div>
  );
}
