import { useEffect, useState } from 'react';
import { recommendationDataApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import type { RecommendationDataConsent } from '../../api/types';

const POLICY_VERSION = 'recommendation-events-v1';

export default function RecommendationDataPrivacy() {
  const [consent, setConsent] = useState<RecommendationDataConsent | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    recommendationDataApi.consent().then(setConsent).catch((e) => setError(parseError(e).message));
  }, []);

  async function update(granted: boolean) {
    setError('');
    setBusy(true);
    try {
      setConsent(await recommendationDataApi.updateConsent(granted, POLICY_VERSION));
    } catch (e) {
      setError(parseError(e).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Dữ liệu cải thiện gợi ý</h1>
        <p className="muted">Lựa chọn này là tùy chọn và không ảnh hưởng đến chức năng tập luyện.</p>
      </header>

      {error && <p className="notice notice-error" role="alert">{error}</p>}
      {!consent ? <p className="muted">Đang tải lựa chọn...</p> : (
        <section className="panel stack">
          <h2>Những gì được ghi nhận</h2>
          <ul className="list">
            <li>Khi gợi ý thực sự hiển thị và khi bạn chủ động bấm bỏ qua.</li>
            <li>Khi bạn bắt đầu, thay đổi lựa chọn bài, hoàn thành, hủy hoặc workout chưa đạt ngưỡng tối thiểu.</li>
            <li>Feedback về recommendation nếu bạn gửi.</li>
          </ul>
          <p>
            Dữ liệu dùng để tổng hợp hiệu quả recommendation, chất lượng event và cải thiện sản phẩm; hiện chưa dùng để tự huấn luyện hoặc tự thay đổi mô hình.
            Không gửi nội dung check-in, ghi chú tự do hay thông tin sức khỏe chi tiết vào event log.
          </p>
          <p className="muted small">
            Chỉ bắt đầu thu thập sau khi bạn bật lựa chọn. Event được giữ tối đa 90 ngày. Tắt lựa chọn sẽ xóa các event recommendation đã thu thập của tài khoản;
            dữ liệu workout/feedback vận hành thông thường vẫn được giữ theo chức năng ứng dụng. Báo cáo quản trị chỉ hiển thị số liệu tổng hợp,
            không hiển thị event từng người. Phiên bản nội dung: {consent.policyVersion}.
          </p>
          <p role="status">
            Trạng thái hiện tại: <b>{consent.trackingEnabled ? 'Đang đồng ý' : 'Chưa đồng ý'}</b>
            {' · '}Event đang lưu: <b>{consent.storedEventCount}</b>
          </p>
          <div className="row">
            {consent.trackingEnabled ? (
              <button className="btn btn-danger" disabled={busy} onClick={() => update(false)}>
                {busy ? 'Đang lưu...' : 'Tắt và xóa event đã thu thập'}
              </button>
            ) : (
              <button className="btn btn-primary" disabled={busy} onClick={() => update(true)}>
                {busy ? 'Đang lưu...' : 'Đồng ý cho phép ghi event'}
              </button>
            )}
          </div>
        </section>
      )}
    </div>
  );
}
