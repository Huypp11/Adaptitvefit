import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { aiApi } from '../../api/endpoints';
import { orStatus, parseError } from '../../api/errors';
import type { RiskInfo } from '../../api/types';
import { RISK_LABEL } from '../../lib/labels';

export default function Risk() {
  const [risk, setRisk] = useState<RiskInfo | null>(null);
  const [noCheckin, setNoCheckin] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    orStatus(aiApi.today(), [404])
      .then((r) => { if (r.status === 404) setNoCheckin(true); else setRisk(r.data); })
      .catch((e) => setError(parseError(e).message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="notice notice-error" role="alert">{error}</p>;
  if (noCheckin || !risk) {
    return (
      <div className="stack-lg">
        <header className="page-head"><h1>Phân tích AI</h1></header>
        <p className="notice notice-warn">Bạn chưa check-in hôm nay. <Link to="/checkin">Check-in ngay</Link> để xem phân tích.</p>
      </div>
    );
  }

  const levelVi = (RISK_LABEL[risk.riskLevel] ?? risk.riskLevel).toLowerCase();

  if (risk.source !== 'model' || risk.riskScore === null) {
    return (
      <div className="stack-lg">
        <header className="page-head"><h1>Phân tích AI</h1></header>
        <section className="panel stack">
          <p>Mức nguy cơ tạm tính theo lịch sử tập luyện: <span className={`level ${risk.riskLevel}`}>{RISK_LABEL[risk.riskLevel] ?? risk.riskLevel}</span></p>
          {risk.notes.map((n) => <p key={n} className="notice notice-warn">{n}</p>)}
          <p className="muted small">Khi dịch vụ AI chạy và có mô hình được kích hoạt, trang này sẽ hiện điểm nguy cơ và giải thích chi tiết.</p>
        </section>
      </div>
    );
  }

  const maxAbs = Math.max(1, ...risk.factors.map((f) => Math.abs(f.impactPp)));
  const score = Math.round(risk.riskScore * 100);
  const base = risk.baseRisk !== null ? Math.round(risk.baseRisk * 100) : null;

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Vì sao nguy cơ của tôi {levelVi}?</h1>
        <p className="muted">Phần giải thích cho dự đoán của mô hình AI về khả năng bạn không tập luyện trong 7 ngày tới.</p>
      </header>

      {risk.demoModel && (
        <p className="notice notice-warn">Đây là mô hình DEMO huấn luyện trên dữ liệu mô phỏng, chỉ mang tính minh hoạ, chưa phải kết quả nghiên cứu thực tế.</p>
      )}

      <section className="panel stack">
        <div className="row" style={{ gap: 20 }}>
          <div className="score">{score}%</div>
          <div className="stack" style={{ gap: 6 }}>
            <span className={`level ${risk.riskLevel}`}>Mức {levelVi}</span>
            {base !== null && <span className="muted small">Mức nền của một người dùng điển hình: {base}%</span>}
          </div>
        </div>
        {risk.summary && <p>{risk.summary}</p>}
      </section>

      <section className="panel stack">
        <h2>Các yếu tố đóng góp nhiều nhất</h2>
        <p className="muted small">
          Mỗi thanh cho biết yếu tố đó đang góp phần làm dự đoán tăng hay giảm bao nhiêu điểm phần trăm (xấp xỉ),
          so với một người dùng điển hình.
        </p>
        <div>
          {risk.factors.length === 0 && <p className="muted">Không có yếu tố nào nổi bật: hồ sơ của bạn gần với người dùng điển hình.</p>}
          {risk.factors.map((f) => {
            const inc = f.direction === 'increase';
            const width = (Math.abs(f.impactPp) / maxAbs) * 50;
            return (
              <div className="factor" key={f.feature}>
                <div className="row-between">
                  <b>{inc ? '↑' : '↓'} {f.label}</b>
                  <span className="small">{f.impactPp > 0 ? '+' : ''}{f.impactPp.toFixed(1)} điểm</span>
                </div>
                <div className="dv-track" aria-hidden="true">
                  <i className={`dv-fill ${f.direction}`} style={inc ? { left: '50%', width: `${width}%` } : { right: '50%', width: `${width}%` }} />
                </div>
                <p className="small muted">{f.text}</p>
              </div>
            );
          })}
        </div>
        <div className="dv-legend"><span>← góp phần làm giảm</span><span>góp phần làm tăng →</span></div>
        {risk.otherImpactPp !== null && Math.abs(risk.otherImpactPp) >= 0.1 && (
          <p className="muted small">Các yếu tố nhỏ còn lại: {risk.otherImpactPp > 0 ? '+' : ''}{risk.otherImpactPp.toFixed(1)} điểm.</p>
        )}
      </section>

      <section className="stack">
        {risk.notes.map((n) => <p key={n} className="muted small">{n}</p>)}
        <p className="muted small">
          Mô hình {risk.algorithm ?? ''} phiên bản {risk.modelVersion ?? ''}. Hệ thống chỉ hỗ trợ duy trì thói quen vận động, không phải công cụ chẩn đoán hay tư vấn y tế.
        </p>
        <div><Link className="btn btn-primary" to="/today">Xem gợi ý bài tập hôm nay</Link></div>
      </section>
    </div>
  );
}
