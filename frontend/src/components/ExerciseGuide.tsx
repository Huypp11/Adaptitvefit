import type { Exercise } from '../api/types';
import { EXERCISE_LOCATIONS, INTENSITIES, labelOf } from '../lib/labels';
import { estimateKcal, metFor } from '../lib/calories';
import ExerciseImage from './ExerciseImage';
import Modal from './Modal';

interface Props { exercise: Exercise; weightKg: number | null; onClose: () => void }

export default function ExerciseGuide({ exercise: e, weightKg, onClose }: Props) {
  const met = metFor(e.met, e.intensity);
  const steps = e.steps.length > 0 ? e.steps : (e.instructions ? [e.instructions] : []);
  const kcalMin = weightKg ? estimateKcal(met, weightKg, e.durationMin) : null;
  const kcalMax = weightKg ? estimateKcal(met, weightKg, e.durationMax) : null;

  return (
    <Modal title={e.name} onClose={onClose}>
      <div className="guide-grid">
        <ExerciseImage media={e.media} alt={`Minh họa bài ${e.name}`} />
        <div className="stack" style={{ gap: 10 }}>
          {e.description && <p>{e.description}</p>}
          <p className="facts small">
            <span>Thời lượng <b>{e.durationMin}-{e.durationMax} phút</b></span>
            <span>Cường độ <b>{labelOf(INTENSITIES, e.intensity)}</b></span>
            <span>Nơi tập <b>{labelOf(EXERCISE_LOCATIONS, e.location)}</b></span>
            {e.equipment && e.equipment !== 'NONE' && <span>Dụng cụ <b>{e.equipment}</b></span>}
          </p>
          <p className="small">
            {kcalMin !== null
              ? <>Ước tính tiêu thụ khoảng <b>{kcalMin}-{kcalMax} kcal</b> (MET {met}). Con số thực tế khác nhau theo từng người.</>
              : <>Điền cân nặng trong Hồ sơ để xem ước tính calo (MET {met}).</>}
          </p>
          {e.tags.length > 0 && <div className="tags">{e.tags.map((t) => <span key={t} className="badge">{t}</span>)}</div>}
        </div>
      </div>
      {steps.length > 0 && (
        <div className="stack" style={{ gap: 8 }}>
          <h3>Cách thực hiện</h3>
          <ol className="steps">{steps.map((s, i) => <li key={i}>{s}</li>)}</ol>
        </div>
      )}
      {e.tips && <p className="notice"><b>Lưu ý:</b> {e.tips}</p>}
      <p className="muted small">Dừng ngay nếu bạn thấy đau. Nội dung chỉ mang tính hướng dẫn chung, không thay thế tư vấn chuyên môn.</p>
    </Modal>
  );
}
