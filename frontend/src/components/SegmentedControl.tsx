interface Option<T extends string> {
  value: T;
  label: string;
  disabled?: boolean;
}

interface Props<T extends string> {
  label: string;
  value: T;
  options: Option<T>[];
  onChange: (value: T) => void;
}

/** A small radio group drawn as a segmented button: used for the time range and the HTTP method. */
export function SegmentedControl<T extends string>({ label, value, options, onChange }: Props<T>) {
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={option.value === value}
          disabled={option.disabled}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
