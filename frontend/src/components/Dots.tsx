export default function Dots({ value, max = 5, label }: { value: number; max?: number; label: string }) {
  return (
    <span className="dots" role="img" aria-label={`${label}: ${value}/${max}`}>
      {Array.from({ length: max }, (_, i) => <i key={i} className={i < value ? 'on' : ''} />)}
    </span>
  );
}
