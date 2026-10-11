import { useEffect, useState, type FormEvent } from 'react';
import { adminInterventionApi } from '../../api/endpoints';
import { parseError } from '../../api/errors';
import type { Intervention } from '../../api/types';
import Field from '../../components/Field';
import Modal from '../../components/Modal';
import { STRATEGY_LABEL } from '../../lib/labels';

const FIELDS = ['sleep_hours', 'stress_level', 'energy_level', 'mood', 'motivation', 'free_time_min',
  'missed_days', 'workout_frequency_7d', 'risk_rank'];

export default function AdminInterventions() {
  const [items, setItems] = useState<Intervention[] | null>(null);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState<Intervention | null>(null);

  const load = () => adminInterventionApi.list().then(setItems).catch((e) => setError(parseError(e).message));
  useEffect(() => { load(); }, []);

  return (
    <div className="stack-lg">
      <header className="page-head">
        <h1>Quy tắc can thiệp</h1>
        <p className="muted">
          Quy tắc có độ ưu tiên nhỏ hơn được xét trước; quy tắc đầu tiên thỏa điều kiện sẽ quyết định chiến lược hôm nay.
          Nếu không quy tắc nào thỏa, hệ thống dùng NORMAL.
        </p>
      </header>

      {error && <p className="notice notice-error" role="alert">{error}</p>}
      {!items ? <p className="muted">Đang tải...</p> : (
        <section className="panel table-wrap">
          <table className="table">
            <thead><tr><th>Ưu tiên</th><th>Chiến lược</th><th>Điều kiện</th><th>Ràng buộc bài tập</th><th>Trạng thái</th><th /></tr></thead>
            <tbody>
              {items.map((i) => (
                <tr key={i.id} className={i.active ? '' : 'off'}>
                  <td>{i.priority}</td>
                  <td><b>{STRATEGY_LABEL[i.code] ?? i.code}</b><br /><span className="muted small">{i.code}</span></td>
                  <td className="mono">{i.conditionExpr}</td>
                  <td className="mono">{i.paramsExpr || '(không)'}</td>
                  <td><span className={`badge ${i.active ? 'badge-ok' : 'badge-off'}`}>{i.active ? 'Đang bật' : 'Đã tắt'}</span></td>
                  <td><button className="btn" onClick={() => setEditing(i)}>Sửa</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <section className="panel stack">
        <h2>Cú pháp</h2>
        <p className="small">
          <b>Điều kiện:</b> nhóm cách nhau bởi <span className="mono">|</span> (HOẶC), điều kiện trong nhóm cách nhau bởi{' '}
          <span className="mono">&amp;</span> (VÀ), <span className="mono">*</span> là luôn đúng. Phép so sánh:{' '}
          <span className="mono">&gt;= &lt;= &gt; &lt; == !=</span>. Ví dụ:{' '}
          <span className="mono">missed_days&gt;=3 | risk_rank&gt;=HIGH&amp;missed_days&gt;=2</span>
        </p>
        <p className="small"><b>Trường được dùng:</b> <span className="mono">{FIELDS.join(', ')}</span> (risk_rank nhận LOW, MEDIUM, HIGH).</p>
        <p className="small">
          <b>Ràng buộc bài tập</b> (khoá=giá trị, cách nhau bởi <span className="mono">;</span>):{' '}
          <span className="mono">maxDurationMin, maxIntensity, minIntensity, requireRecovery, difficultyShift</span>. Ví dụ:{' '}
          <span className="mono">maxDurationMin=15;maxIntensity=LOW;difficultyShift=-1</span>
        </p>
        <p className="muted small">Mức nguy cơ hiện được tạm tính theo lịch sử tập luyện; khi mô hình AI được kết nối sẽ thay bằng điểm từ mô hình.</p>
      </section>

      {editing && (
        <EditForm item={editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); }} />
      )}
    </div>
  );
}

function EditForm({ item, onClose, onSaved }: { item: Intervention; onClose: () => void; onSaved: () => void }) {
  const [priority, setPriority] = useState(String(item.priority));
  const [condition, setCondition] = useState(item.conditionExpr ?? '');
  const [params, setParams] = useState(item.paramsExpr ?? '');
  const [description, setDescription] = useState(item.description ?? '');
  const [active, setActive] = useState(item.active);
  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(''); setFields({}); setBusy(true);
    try {
      await adminInterventionApi.update(item.id, {
        priority: Number(priority), conditionExpr: condition, paramsExpr: params,
        description: description || undefined, active,
      });
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
    <Modal title={`Sửa quy tắc ${item.code}`} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        {error && <p className="notice notice-error" role="alert">{error}</p>}
        <Field label="Độ ưu tiên (số nhỏ xét trước)" htmlFor="pr" error={fields.priority}>
          <input id="pr" className="input" type="number" min="1" max="100" required value={priority} onChange={(e) => setPriority(e.target.value)} />
        </Field>
        <Field label="Điều kiện" htmlFor="cond" error={fields.conditionExpr}>
          <input id="cond" className="input mono" required value={condition} onChange={(e) => setCondition(e.target.value)} />
        </Field>
        <Field label="Ràng buộc chọn bài tập" htmlFor="par" error={fields.paramsExpr}>
          <input id="par" className="input mono" value={params} onChange={(e) => setParams(e.target.value)} />
        </Field>
        <Field label="Mô tả hiển thị cho người dùng" htmlFor="desc" error={fields.description}>
          <input id="desc" className="input" value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <label className="row">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          Đang bật
        </label>
        <div className="row">
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu quy tắc'}</button>
          <button type="button" className="btn" onClick={onClose}>Huỷ</button>
        </div>
      </form>
    </Modal>
  );
}
