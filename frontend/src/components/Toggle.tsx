interface Props {
  value: boolean;
  onChange: (v: boolean) => void;
  yes: string;
  no: string;
  label: string;
}

export default function Toggle({ value, onChange, yes, no, label }: Props) {
  return (
    <fieldset className="field">
      <legend>{label}</legend>
      <div className="toggle">
        <button type="button" aria-pressed={value} onClick={() => onChange(true)}>{yes}</button>
        <button type="button" aria-pressed={!value} onClick={() => onChange(false)}>{no}</button>
      </div>
    </fieldset>
  );
}
