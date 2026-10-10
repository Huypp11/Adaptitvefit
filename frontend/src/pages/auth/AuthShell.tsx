import type { ReactNode } from 'react';

export default function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="auth">
      <section className="auth-side">
        <div className="brand" style={{ padding: 0 }}>Adaptive<span>Fit</span></div>
        <h1>Giữ thói quen tập luyện, kể cả những ngày mệt.</h1>
        <p>Check-in mỗi ngày để hệ thống giúp bạn chọn buổi tập vừa sức với trạng thái hiện tại.</p>
      </section>
      <section className="auth-form">
        <div className="stack-lg">
          <h2 style={{ fontSize: '1.6rem' }}>{title}</h2>
          {children}
        </div>
      </section>
    </div>
  );
}
