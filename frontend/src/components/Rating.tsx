interface Props {
  label: string;
  value: number | null;
  onChange: (n: number) => void;
  low: string;
  high: string;
  error?: string;
}

export default function Rating({ label, value, onChange, low, high, error }: Props) {
  function handleKeyDown(event: React.KeyboardEvent<HTMLButtonElement>, index: number) {
    let nextIndex: number;
    switch (event.key) {
      case 'ArrowRight':
      case 'ArrowUp':
        nextIndex = Math.min(4, index + 1);
        break;
      case 'ArrowLeft':
      case 'ArrowDown':
        nextIndex = Math.max(0, index - 1);
        break;
      case 'Home':
        nextIndex = 0;
        break;
      case 'End':
        nextIndex = 4;
        break;
      default:
        return;
    }
    event.preventDefault();
    event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role="radio"]')[nextIndex]?.focus();
    onChange(nextIndex + 1);
  }

  return (
    <fieldset className="field">
      <legend>{label}</legend>
      <div className="rating" role="radiogroup" aria-label={label}>
        {[1, 2, 3, 4, 5].map((n, index) => (
          <button key={n} type="button" role="radio" aria-checked={value === n}
                  tabIndex={value === n || (value === null && n === 1) ? 0 : -1}
                  onKeyDown={(event) => handleKeyDown(event, index)}
                  onClick={() => onChange(n)}>
            {n}
          </button>
        ))}
      </div>
      <div className="rating-ends"><span>{low}</span><span>{high}</span></div>
      {error && <p className="err">{error}</p>}
    </fieldset>
  );
}
