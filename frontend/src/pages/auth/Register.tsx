import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { parseError } from '../../api/errors';
import Field from '../../components/Field';
import AuthShell from './AuthShell';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setFields({});
    if (password !== confirm) {
      setFields({ confirm: 'Mật khẩu nhập lại không khớp' });
      return;
    }
    setBusy(true);
    try {
      await register(email, password);
      navigate('/profile', { replace: true });
    } catch (err) {
      const info = parseError(err);
      setError(info.message);
      setFields(info.fields);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Tạo tài khoản">
      <form className="stack" onSubmit={submit}>
        {error && <p className="notice notice-error" role="alert">{error}</p>}
        <Field label="Email" htmlFor="email" error={fields.email}>
          <input id="email" className="input" type="email" autoComplete="email" required
                 value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Mật khẩu" htmlFor="password" hint="Từ 8 đến 72 ký tự" error={fields.password}>
          <input id="password" className="input" type="password" autoComplete="new-password" required minLength={8}
                 value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field label="Nhập lại mật khẩu" htmlFor="confirm" error={fields.confirm}>
          <input id="confirm" className="input" type="password" autoComplete="new-password" required
                 value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        </Field>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Đang tạo...' : 'Tạo tài khoản'}</button>
        <p className="muted">Đã có tài khoản? <Link to="/login">Đăng nhập</Link></p>
      </form>
    </AuthShell>
  );
}
