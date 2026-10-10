import type { Exercise } from '../api/types';
import { EXERCISE_LOCATIONS, INTENSITIES, labelOf } from '../lib/labels';
import { estimateKcal, metFor } from '../lib/calories';
import Dots from './Dots';
import ExerciseImage from './ExerciseImage';

interface Props {
  exercise: Exercise;
  weightKg?: number | null;
  selected?: boolean;
  onToggle?: (id: number) => void;
  onOpenGuide?: (e: Exercise) => void;
}

export default function ExerciseCard({ exercise: e, weightKg, selected, onToggle, onOpenGuide }: Props) {
  const kcal = weightKg ? estimateKcal(metFor(e.met, e.intensity), weightKg, e.durationMin) : null;
  const body = (
    <div className="stack" style={{ gap: 6 }}>
      <h3>{e.name}</h3>
      {e.description && <p className="muted small">{e.description}</p>}
      <p className="small">
        {e.durationMin === e.durationMax ? `${e.durationMin} phút` : `${e.durationMin}-${e.durationMax} phút`}
        {' · '}{labelOf(INTENSITIES, e.intensity)}
        {' · '}{labelOf(EXERCISE_LOCATIONS, e.location)}
        {kcal !== null && <> · khoảng {kcal} kcal</>}
      </p>
      <p className="small">Độ khó <Dots value={e.difficulty} label="Độ khó" /></p>
    </div>
  );

  return (
    <article className={`ex-card ${selected ? 'selected' : ''}`}>
      <ExerciseImage media={e.media} alt={`Minh họa bài ${e.name}`} />
      {onToggle ? (
        <label className="pick">
          <input type="checkbox" checked={!!selected} onChange={() => onToggle(e.id)} aria-label={`Chọn ${e.name}`} />
          {body}
        </label>
      ) : body}
      {e.tags.length > 0 && <div className="tags">{e.tags.map((t) => <span key={t} className="badge">{t}</span>)}</div>}
      {onOpenGuide && (
        <div><button type="button" className="btn" onClick={() => onOpenGuide(e)}>Xem hướng dẫn</button></div>
      )}
    </article>
  );
}
