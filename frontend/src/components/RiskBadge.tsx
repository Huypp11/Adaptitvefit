type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export function riskLevelForScore(score: number | null | undefined): RiskLevel | 'UNKNOWN' {
  if (score === null || score === undefined || !Number.isFinite(score)) return 'UNKNOWN';
  return score <= 0.30 ? 'LOW' : score <= 0.60 ? 'MEDIUM' : 'HIGH';
}

export default function RiskBadge({ level, score }: { level?: string | null; score?: number | null }) {
  const knownLevel = score === undefined ? (level ?? '').toUpperCase() : riskLevelForScore(score);
  const tone: RiskLevel | 'UNKNOWN' =
    knownLevel === 'LOW' || knownLevel === 'MEDIUM' || knownLevel === 'HIGH' ? knownLevel : 'UNKNOWN';
  const label = knownLevel === 'LOW' ? 'thấp' : knownLevel === 'MEDIUM' ? 'cần chú ý'
    : knownLevel === 'HIGH' ? 'cao' : 'chưa xác định';

  return (
    <span className={`risk-badge risk-badge-${tone.toLowerCase()}`} role="status"
          aria-label={`Nguy cơ bỏ lỡ buổi tập ${label}`}>
      <span className="risk-badge-dot" aria-hidden="true" />
      Nguy cơ bỏ lỡ buổi tập: {label}
    </span>
  );
}
