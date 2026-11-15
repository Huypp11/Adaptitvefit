interface Props {
  id: string;
  label: string;
  value: number | null;
  min: number;
  max: number;
  step: number;
  suffix: string;
  lowLabel: string;
  highLabel: string;
  error?: string;
  onChange: (value: number) => void;
}

export default function RangeField({
  id, label, value, min, max, step, suffix, lowLabel, highLabel, error, onChange,
}: Props) {
  const errorId = `${id}-error`;

  return (
    <fieldset className="field range-field">
      <legend>{label}</legend>
      <output className="range-value" htmlFor={id}>
        {value === null ? 'Chưa chọn' : `${value} ${suffix}`}
      </output>
      <input
        id={id}
        className="range-input"
        type="range"
        min={min}
        max={max}
        step={step}
        value={value ?? min}
        aria-label={label}
        aria-valuetext={value === null ? 'Chưa chọn' : `${value} ${suffix}`}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
        onChange={(event) => onChange(Number(event.target.value))}
      />
      <div className="range-ends" aria-hidden="true">
        <span>{lowLabel}</span>
        <span>{highLabel}</span>
      </div>
      {value === null && <p className="hint">Kéo thanh trượt hoặc dùng phím mũi tên để chọn.</p>}
      {error && <p className="err" id={errorId}>{error}</p>}
    </fieldset>
  );
}
