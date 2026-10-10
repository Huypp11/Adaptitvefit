import { useEffect, useState, type FormEvent } from 'react';
import { profileApi } from '../../api/endpoints';
import { orNull, parseError } from '../../api/errors';
import type { Profile as ProfileData } from '../../api/types';
import Field from '../../components/Field';
import { FITNESS_LEVELS, GOALS, LOCATIONS } from '../../lib/labels';

interface FormState {
  age: string; heightCm: string; weightKg: string; fitnessLevel: string; goal: string;
  availableMinutes: string; preferredWorkout: string; location: string; equipment: string;
}

const empty: FormState = {
  age: '', heightCm: '', weightKg: '', fitnessLevel: 'BEGINNER', goal: 'STAY_ACTIVE',
  availableMinutes: '30', preferredWorkout: '', location: 'HOME', equipment: '',
};

export default function Profile() {
  const [form, setForm] = useState<FormState>(empty);
  const [loading, setLoading] = useState(true);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [hadProfile, setHadProfile] = useState(false);

  useEffect(() => {
    orNull(profileApi.get())
      .then((p) => {
        if (p) {
          setHadProfile(true);
          setForm({
            age: String(p.age), heightCm: String(p.heightCm), weightKg: String(p.weightKg),
            fitnessLevel: p.fitnessLevel, goal: p.goal, availableMinutes: String(p.availableMinutes),
            preferredWorkout: p.preferredWorkout ?? '', location: p.location, equipment: p.equipment ?? '',
          });
        }
      })
      .catch((e) => setError(parseError(e).message))
      .finally(() => setLoading(false));
  }, []);

  const set = (k: keyof FormState) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(''); setFields({}); setSaved(false);
    const payload: ProfileData = {
      age: Number(form.age), heightCm: Number(form.heightCm), weightKg: Number(form.weightKg),
      fitnessLevel: form.fitnessLevel, goal: form.goal, availableMinutes: Number(form.availableMinutes),
      preferredWorkout: form.preferredWorkout.trim() || null, location: form.location,
      equipment: form.equipment.trim() || null,
    };
    try {
      await profileApi.save(payload);
      setSaved(true);
      setHadProfile(true);
    } catch (err) {
      const info = parseError(err);
      setError(info.message);
      setFields(info.fields);
    }
  }

  if (loading) return <p className="muted">Đang tải hồ sơ...</p>;

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Hồ sơ của bạn</h1>
        <p className="muted">Chỉ những thông tin cần để gợi ý buổi tập phù hợp.</p>
      </header>

      {!hadProfile && <p className="notice">Bạn chưa có hồ sơ. Điền các mục dưới đây để bắt đầu.</p>}
      {error && <p className="notice notice-error" role="alert">{error}</p>}
      {saved && <p className="notice notice-ok" role="status">Đã lưu hồ sơ.</p>}

      <form className="panel stack" onSubmit={submit}>
        <div className="form-grid">
          <Field label="Tuổi" htmlFor="age" error={fields.age}>
            <input id="age" className="input" type="number" required value={form.age} onChange={set('age')} />
          </Field>
          <Field label="Chiều cao (cm)" htmlFor="h" error={fields.heightCm}>
            <input id="h" className="input" type="number" step="0.1" required value={form.heightCm} onChange={set('heightCm')} />
          </Field>
          <Field label="Cân nặng (kg)" htmlFor="w" error={fields.weightKg}>
            <input id="w" className="input" type="number" step="0.1" required value={form.weightKg} onChange={set('weightKg')} />
          </Field>
          <Field label="Thời gian có thể tập mỗi ngày (phút)" htmlFor="m" error={fields.availableMinutes}>
            <input id="m" className="input" type="number" required value={form.availableMinutes} onChange={set('availableMinutes')} />
          </Field>
          <Field label="Mức thể lực hiện tại" htmlFor="fl" error={fields.fitnessLevel}>
            <select id="fl" className="input" value={form.fitnessLevel} onChange={set('fitnessLevel')}>
              {FITNESS_LEVELS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
          <Field label="Mục tiêu" htmlFor="g" error={fields.goal}>
            <select id="g" className="input" value={form.goal} onChange={set('goal')}>
              {GOALS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
          <Field label="Nơi tập thường xuyên" htmlFor="loc" error={fields.location}>
            <select id="loc" className="input" value={form.location} onChange={set('location')}>
              {LOCATIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
          <Field label="Hình thức tập yêu thích" htmlFor="pw" hint="Ví dụ: đi bộ, yoga, chạy bộ" error={fields.preferredWorkout}>
            <input id="pw" className="input" value={form.preferredWorkout} onChange={set('preferredWorkout')} />
          </Field>
          <Field label="Dụng cụ có sẵn" htmlFor="eq" hint="Ví dụ: thảm yoga, tạ tay. Để trống nếu không có" error={fields.equipment} className="full">
            <input id="eq" className="input" value={form.equipment} onChange={set('equipment')} />
          </Field>
        </div>
        <div><button className="btn btn-primary">Lưu hồ sơ</button></div>
      </form>
    </div>
  );
}
