interface Option<T extends string> { value: T; label: string }

interface Props<T extends string> {
  label: string;
  value: T;
  options: Option<T>[];
  onChange: (v: T) => void;
}

/** Chon mot trong nhieu lua chon (dung style cua Toggle). */
export default function Segmented<T extends string>({ label, value, options, onChange }: Props<T>) {
  function handleKeyDown(event: React.KeyboardEvent<HTMLButtonElement>, index: number) {
    let nextIndex: number;
    switch (event.key) {
      case 'ArrowRight':
      case 'ArrowDown':
        nextIndex = (index + 1) % options.length;
        break;
      case 'ArrowLeft':
      case 'ArrowUp':
        nextIndex = (index - 1 + options.length) % options.length;
        break;
      case 'Home':
        nextIndex = 0;
        break;
      case 'End':
        nextIndex = options.length - 1;
        break;
      default:
        return;
    }
    event.preventDefault();
    const option = options[nextIndex];
    event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role="radio"]')[nextIndex]?.focus();
    onChange(option.value);
  }

  return (
    <fieldset className="field">
      <legend>{label}</legend>
      <div className="toggle wrap" role="radiogroup" aria-label={label}>
        {options.map((o) => (
          <button key={o.value} type="button" role="radio" aria-checked={value === o.value}
                  aria-pressed={value === o.value} tabIndex={value === o.value ? 0 : -1}
                  onKeyDown={(event) => handleKeyDown(event, options.indexOf(o))}
                  onClick={() => onChange(o.value)}>
            {o.label}
          </button>
        ))}
      </div>
    </fieldset>
  );
}
