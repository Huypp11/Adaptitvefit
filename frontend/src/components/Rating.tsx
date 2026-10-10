interface Props {
  label: string;
  value: number | null;
  onChange: (n: number) => void;
  low: string;
  high: string;
  error?: string;
}

export default function Rating({ label, value, onChange, low, high, error }: Props) {
  return (
    <fieldset className="field">
      <legend>{label}</legend>
      <div className="rating" role="radiogroup" aria-label={label}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" role="radio" aria-checked={value === n} onClick={() => onChange(n)}>
            {n}
          </button>
        ))}
      </div>
      <div className="rating-ends"><span>{low}</span><span>{high}</span></div>
      {error && <p className="err">{error}</p>}
    </fieldset>
  );
}
