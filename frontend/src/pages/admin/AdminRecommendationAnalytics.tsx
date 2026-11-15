import { useEffect, useState } from 'react';
import { recommendationDataApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import type { RecommendationAnalytics } from '../../api/types';

export default function AdminRecommendationAnalytics() {
  const [report, setReport] = useState<RecommendationAnalytics | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    recommendationDataApi.analytics().then(setReport).catch((e) => setError(parseError(e).message));
  }, []);

  if (error) return <p className="notice notice-error" role="alert">{error}</p>;
  if (!report) return <p className="muted">Đang tải báo cáo...</p>;

  const rate = report.completionRateWithin24Hours === null
    ? 'Chưa đủ dữ liệu'
    : `${Math.round(report.completionRateWithin24Hours * 100)}%`;
  const percent = (value: number | null) => value === null
    ? 'Chưa đủ dữ liệu'
    : `${Math.round(value * 100)}%`;
  const values: Array<[string, number | string]> = [
    ['Người đang đồng ý', report.usersWithCurrentConsent],
    ['Gợi ý CHECKIN đã hiển thị', report.displayedCheckinJourneys],
    ['Gợi ý PROFILE_FALLBACK đã hiển thị', report.displayedFallbackJourneys],
    ['Đã đủ cửa sổ theo dõi 24 giờ', report.matureJourneys],
    ['Hoàn thành hợp lệ trong 24 giờ', report.completedValidWithin24Hours],
    ['Tỷ lệ outcome chính', rate],
    ['CHECKIN · đủ 24 giờ / mẫu', report.matureCheckinJourneys],
    ['CHECKIN · hoàn thành hợp lệ', report.completedCheckinWithin24Hours],
    ['CHECKIN · tỷ lệ trong 24 giờ', percent(report.completionRateCheckinWithin24Hours)],
    ['PROFILE_FALLBACK · đủ 24 giờ / mẫu', report.matureFallbackJourneys],
    ['PROFILE_FALLBACK · hoàn thành hợp lệ', report.completedFallbackWithin24Hours],
    ['PROFILE_FALLBACK · tỷ lệ trong 24 giờ', percent(report.completionRateFallbackWithin24Hours)],
    ['Chủ động bỏ qua', report.skippedJourneys],
    ['Đã bắt đầu workout', report.startedJourneys],
    ['Workout bị hủy', report.abandonedJourneys],
    ['Workout INVALID_WORKOUT', report.invalidWorkoutJourneys],
    ['Chưa có feedback', report.journeysWithoutFeedback],
    ['Chưa đủ 24 giờ theo dõi', report.journeysStillWithinObservationWindow],
    ['Lifecycle event không tìm thấy start', report.lifecycleEventsWithoutStart],
    ['Journey bắt đầu nhưng chưa có terminal event', report.journeysWithoutCompletionEvent],
  ];

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Đánh giá recommendation</h1>
        <p className="muted">Dữ liệu {report.windowDays} ngày gần nhất, chỉ từ người đã đồng ý và chưa rút lại.</p>
      </header>
      <section className="grid-2">
        {values.map(([label, value]) => (
          <article className="panel stat" key={label}>
            <span className="muted">{label}</span>
            <b>{value}</b>
          </article>
        ))}
      </section>
      <p className="notice">{report.note}</p>
    </div>
  );
}
