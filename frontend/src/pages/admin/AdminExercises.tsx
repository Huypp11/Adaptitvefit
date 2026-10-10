import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { adminExerciseApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import type { Exercise, ExerciseInput, PageResponse } from '../../api/types';
import Field from '../../components/Field';
import Modal from '../../components/Modal';
import Pagination from '../../components/Pagination';
import { EXERCISE_GOALS, EXERCISE_LOCATIONS, INTENSITIES, labelOf } from '../../lib/labels';

interface FormState {
  name: string; description: string; durationMin: string; durationMax: string; difficulty: string;
  intensity: string; equipment: string; location: string; muscleGroup: string; goal: string;
  energyRequirement: string; recoverySuitable: boolean; met: string; steps: string; media: string; tips: string;
  active: boolean; tags: string;
}

const blank: FormState = {
  name: '', description: '', durationMin: '10', durationMax: '20', difficulty: '2', intensity: 'LOW',
  equipment: '', location: 'HOME', muscleGroup: '', goal: '', energyRequirement: '2',
  recoverySuitable: false, met: '', steps: '', media: '', tips: '', active: true, tags: '',
};

function fromExercise(e: Exercise): FormState {
  return {
    name: e.name, description: e.description ?? '', durationMin: String(e.durationMin),
    durationMax: String(e.durationMax), difficulty: String(e.difficulty), intensity: e.intensity,
    equipment: e.equipment ?? '', location: e.location, muscleGroup: e.muscleGroup ?? '', goal: e.goal ?? '',
    energyRequirement: String(e.energyRequirement), recoverySuitable: e.recoverySuitable,
    met: e.met === null ? '' : String(e.met), steps: e.steps.join('\n'), media: e.media.join('\n'), tips: e.tips ?? '',
    active: e.active, tags: e.tags.join(', '),
  };
}

const lines = (s: string) => s.split('\n').map((x) => x.trim()).filter(Boolean);

function toInput(f: FormState): ExerciseInput {
  const opt = (s: string) => (s.trim() ? s.trim() : undefined);
  return {
    name: f.name.trim(), description: opt(f.description), durationMin: Number(f.durationMin),
    durationMax: Number(f.durationMax), difficulty: Number(f.difficulty), intensity: f.intensity,
    equipment: opt(f.equipment), location: f.location, muscleGroup: opt(f.muscleGroup), goal: opt(f.goal),
    energyRequirement: Number(f.energyRequirement), recoverySuitable: f.recoverySuitable,
    met: f.met.trim() ? Number(f.met) : undefined, steps: lines(f.steps), media: lines(f.media), tips: opt(f.tips),
    active: f.active,
    tags: f.tags.split(',').map((t) => t.trim().toLowerCase()).filter(Boolean),
  };
}

export default function AdminExercises() {
  const [q, setQ] = useState('');
  const [tag, setTag] = useState('');
  const [active, setActive] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<Exercise> | null>(null);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState<{ id: number | null; form: FormState } | null>(null);

  const load = useCallback(() => {
    adminExerciseApi
      .list({ q: q || undefined, tag: tag || undefined, active: active === '' ? undefined : active === 'true', page, size: 15 })
      .then((r) => { setResult(r); setError(''); })
      .catch((e) => setError(parseError(e).message));
  }, [q, tag, active, page]);

  useEffect(() => {
    const t = setTimeout(load, 250);
    return () => clearTimeout(t);
  }, [load]);

  async function deactivate(e: Exercise) {
    if (!window.confirm(`Tắt bài tập "${e.name}"? Người dùng sẽ không thấy bài này nữa, lịch sử tập vẫn được giữ.`)) return;
    try {
      await adminExerciseApi.deactivate(e.id);
      load();
    } catch (err) {
      setError(parseError(err).message);
    }
  }

  return (
    <div className="stack-lg">
      <header className="page-head">
        <div className="row-between">
          <h1>Quản lý bài tập</h1>
          <button className="btn btn-primary" onClick={() => setEditing({ id: null, form: blank })}>Thêm bài tập</button>
        </div>
        <p className="muted">Thư viện này là nguồn duy nhất cho gợi ý bài tập. Tắt một bài sẽ ẩn nó khỏi người dùng.</p>
      </header>

      <div className="panel form-grid">
        <div className="field">
          <label htmlFor="q">Tìm theo tên</label>
          <input id="q" className="input" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        <div className="field">
          <label htmlFor="tag">Thẻ</label>
          <input id="tag" className="input" value={tag} onChange={(e) => { setTag(e.target.value); setPage(0); }} />
        </div>
        <div className="field">
          <label htmlFor="act">Trạng thái</label>
          <select id="act" className="input" value={active} onChange={(e) => { setActive(e.target.value); setPage(0); }}>
            <option value="">Tất cả</option>
            <option value="true">Đang bật</option>
            <option value="false">Đã tắt</option>
          </select>
        </div>
      </div>

      {error && <p className="notice notice-error" role="alert">{error}</p>}

      {!result ? <p className="muted">Đang tải...</p> : (
        <section className="panel table-wrap">
          <table className="table">
            <thead>
              <tr><th>Tên</th><th>Thời lượng</th><th>Cường độ</th><th>Nơi tập</th><th>Thẻ</th><th>Trạng thái</th><th /></tr>
            </thead>
            <tbody>
              {result.content.map((e) => (
                <tr key={e.id} className={e.active ? '' : 'off'}>
                  <td><b>{e.name}</b></td>
                  <td>{e.durationMin}-{e.durationMax} phút</td>
                  <td>{labelOf(INTENSITIES, e.intensity)}</td>
                  <td>{labelOf(EXERCISE_LOCATIONS, e.location)}</td>
                  <td><div className="tags">{e.tags.map((t) => <span key={t} className="badge">{t}</span>)}</div></td>
                  <td><span className={`badge ${e.active ? 'badge-ok' : 'badge-off'}`}>{e.active ? 'Đang bật' : 'Đã tắt'}</span></td>
                  <td>
                    <div className="row" style={{ flexWrap: 'nowrap' }}>
                      <button className="btn" onClick={() => setEditing({ id: e.id, form: fromExercise(e) })}>Sửa</button>
                      {e.active && <button className="btn btn-danger" onClick={() => deactivate(e)}>Tắt</button>}
                    </div>
                  </td>
                </tr>
              ))}
              {result.content.length === 0 && <tr><td colSpan={7} className="muted">Không có bài tập nào khớp bộ lọc.</td></tr>}
            </tbody>
          </table>
        </section>
      )}

      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}

      {editing && (
        <ExerciseForm
          id={editing.id}
          initial={editing.form}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); load(); }}
        />
      )}
    </div>
  );
}

function ExerciseForm({ id, initial, onClose, onSaved }: {
  id: number | null; initial: FormState; onClose: () => void; onSaved: () => void;
}) {
  const [f, setF] = useState<FormState>(initial);
  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const set = (k: keyof FormState) => (e: { target: { value: string } }) => setF((s) => ({ ...s, [k]: e.target.value }));

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(''); setFields({}); setBusy(true);
    try {
      const payload = toInput(f);
      if (id === null) await adminExerciseApi.create(payload);
      else await adminExerciseApi.update(id, payload);
      onSaved();
    } catch (err) {
      const info = parseError(err);
      setError(info.message);
      setFields(info.fields);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={id === null ? 'Thêm bài tập' : 'Sửa bài tập'} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        {error && <p className="notice notice-error" role="alert">{error}</p>}
        <div className="form-grid">
          <Field label="Tên bài tập" htmlFor="n" error={fields.name} className="full">
            <input id="n" className="input" required value={f.name} onChange={set('name')} />
          </Field>
          <Field label="Mô tả ngắn" htmlFor="d" error={fields.description} className="full">
            <input id="d" className="input" value={f.description} onChange={set('description')} />
          </Field>
          <Field label="Thời lượng tối thiểu (phút)" htmlFor="dmin" error={fields.durationMin}>
            <input id="dmin" className="input" type="number" required value={f.durationMin} onChange={set('durationMin')} />
          </Field>
          <Field label="Thời lượng tối đa (phút)" htmlFor="dmax" error={fields.durationMax}>
            <input id="dmax" className="input" type="number" required value={f.durationMax} onChange={set('durationMax')} />
          </Field>
          <Field label="Độ khó (1-5)" htmlFor="df" error={fields.difficulty}>
            <input id="df" className="input" type="number" min="1" max="5" required value={f.difficulty} onChange={set('difficulty')} />
          </Field>
          <Field label="Năng lượng cần có (1-5)" htmlFor="en" error={fields.energyRequirement}>
            <input id="en" className="input" type="number" min="1" max="5" required value={f.energyRequirement} onChange={set('energyRequirement')} />
          </Field>
          <Field label="Cường độ" htmlFor="in" error={fields.intensity}>
            <select id="in" className="input" value={f.intensity} onChange={set('intensity')}>
              {INTENSITIES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
          <Field label="Nơi tập" htmlFor="lo" error={fields.location}>
            <select id="lo" className="input" value={f.location} onChange={set('location')}>
              {EXERCISE_LOCATIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
          <Field label="Mục tiêu" htmlFor="go" error={fields.goal}>
            <select id="go" className="input" value={f.goal} onChange={set('goal')}>
              <option value="">Không chỉ định</option>
              {EXERCISE_GOALS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
          <Field label="Nhóm cơ" htmlFor="mg" error={fields.muscleGroup}>
            <input id="mg" className="input" value={f.muscleGroup} onChange={set('muscleGroup')} />
          </Field>
          <Field label="Dụng cụ" htmlFor="eq" hint="Để trống hoặc NONE nếu không cần" error={fields.equipment}>
            <input id="eq" className="input" value={f.equipment} onChange={set('equipment')} />
          </Field>
          <Field label="Thẻ" htmlFor="tg" hint="Cách nhau bằng dấu phẩy; chỉ a-z, 0-9, _ (tối đa 10)" error={fields.tags}>
            <input id="tg" className="input" value={f.tags} onChange={set('tags')} />
          </Field>
          <Field label="MET (để ước tính calo)" htmlFor="met" hint="Ví dụ 3.5. Để trống thì hệ thống dùng giá trị theo cường độ" error={fields.met}>
            <input id="met" className="input" type="number" step="0.1" min="1" max="20" value={f.met} onChange={set('met')} />
          </Field>
          <Field label="Các bước thực hiện" htmlFor="steps" hint="Mỗi dòng là một bước (tối đa 15 bước)" error={fields.steps} className="full">
            <textarea id="steps" className="input" rows={6} value={f.steps} onChange={set('steps')} />
          </Field>
          <Field label="Đường dẫn ảnh minh họa" htmlFor="media" hint="Mỗi dòng một đường dẫn, ví dụ /exercise-images/Pushups/0.jpg (tối đa 5)" error={fields.media} className="full">
            <textarea id="media" className="input" rows={3} value={f.media} onChange={set('media')} />
          </Field>
          <Field label="Lưu ý an toàn" htmlFor="tips" error={fields.tips} className="full">
            <input id="tips" className="input" value={f.tips} onChange={set('tips')} />
          </Field>
        </div>
        <label className="row">
          <input type="checkbox" checked={f.recoverySuitable} onChange={(e) => setF((s) => ({ ...s, recoverySuitable: e.target.checked }))} />
          Phù hợp khi cần phục hồi
        </label>
        <label className="row">
          <input type="checkbox" checked={f.active} onChange={(e) => setF((s) => ({ ...s, active: e.target.checked }))} />
          Đang bật (người dùng nhìn thấy)
        </label>
        <div className="row">
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu bài tập'}</button>
          <button type="button" className="btn" onClick={onClose}>Huỷ</button>
        </div>
      </form>
    </Modal>
  );
}
