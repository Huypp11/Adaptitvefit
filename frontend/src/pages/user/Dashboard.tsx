import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { checkinApi, profileApi, workoutApi } from '../../api/endpoints';
import { orNull, parseError } from '../../api/errors';
import type { Checkin, Profile, WorkoutSummary } from '../../api/types';
import { STATUS_LABEL } from '../../lib/labels';
import { addDays, datePart, formatDateLong, formatDateTime, greeting, toISODate, weekdayLabel } from '../../lib/dates';

interface Data {
  profile: Profile | null;
  checkins: Checkin[];
  workouts: WorkoutSummary[];
}

export default function Dashboard() {
  const [data, setData] = useState<Data | null>(null);
  const [error, setError] = useState('');
  const now = useMemo(() => new Date(), []);

  useEffect(() => {
    const from = toISODate(addDays(now, -6));
    Promise.all([orNull(profileApi.get()), checkinApi.list(from, toISODate(now)), workoutApi.history(0, 50)])
      .then(([profile, checkins, history]) => setData({ profile, checkins, workouts: history.content }))
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

      <div className="grid-2">
        <section className="panel stack">
          <h2>Check-in hôm nay</h2>
          {todayCheckin ? (
            <>
              <p>
                Ngủ <b>{todayCheckin.sleepHours}h</b> · Căng thẳng <b>{todayCheckin.stress}/5</b> · Năng lượng <b>{todayCheckin.energy}/5</b>
              </p>
              <div className="row">
                <Link className="btn btn-primary" to="/exercises">Chọn bài tập</Link>
                <Link className="btn" to="/checkin">Xem chi tiết</Link>
              </div>
            </>
          ) : (
            <>
              <p className="muted">Bạn chưa check-in hôm nay.</p>
              <div><Link className="btn btn-primary" to="/checkin">Check-in ngay</Link></div>
            </>
          )}
        </section>

        <section className="panel stack">
          <h2>Nguy cơ bỏ tập và chiến lược hôm nay</h2>
          <p className="muted">
            Chưa khả dụng. Phần dự đoán sẽ xuất hiện khi mô hình học máy được huấn luyện và kết nối.
            Trong lúc chờ, bạn vẫn có thể tự chọn bài tập trong thư viện.
          </p>
        </section>
      </div>

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
                <span className={`badge ${w.status === 'COMPLETED' ? 'badge-ok' : w.status === 'STARTED' ? 'badge-warn' : ''}`}>
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
