import { ABANDON_REASONS } from '../lib/labels';

interface Props {
  busy: boolean;
  onChoose: (reason: string) => void;
  onSkip: () => void;
}

export default function AbandonReasonDialog({ busy, onChoose, onSkip }: Props) {
  return (
    <div className="overlay">
      <section className="modal stack" role="dialog" aria-modal="true" aria-labelledby="abandon-title">
        <div>
          <h2 id="abandon-title">Điều gì khiến bạn dừng buổi tập?</h2>
          <p className="muted small">Phản hồi này giúp điều chỉnh gợi ý cho ngày tiếp theo.</p>
        </div>
        <div className="stack">
          {ABANDON_REASONS.map((reason) => (
            <button key={reason.value} className="btn" disabled={busy}
                    onClick={() => onChoose(reason.value)}>{reason.label}</button>
          ))}
        </div>
        <button className="btn btn-quiet" disabled={busy} onClick={onSkip}>Bỏ qua lý do</button>
      </section>
    </div>
  );
}
