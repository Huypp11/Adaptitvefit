import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { homeFor } from '../../auth/guards';
import { parseError } from '../../api/errors';
import Field from '../../components/Field';
import AuthShell from './AuthShell';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const u = await login(email, password);
      navigate(homeFor(u.role), { replace: true });
    } catch (err) {
      setError(parseError(err).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Đăng nhập">
      <form className="stack" onSubmit={submit}>
        {error && <p className="notice notice-error" role="alert">{error}</p>}
        <Field label="Email" htmlFor="email">
          <input id="email" className="input" type="email" autoComplete="email" required
                 value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Mật khẩu" htmlFor="password">
          <input id="password" className="input" type="password" autoComplete="current-password" required
                 value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Đang đăng nhập...' : 'Đăng nhập'}</button>
        <p className="muted">Chưa có tài khoản? <Link to="/register">Đăng ký</Link></p>
      </form>
    </AuthShell>
  );
}
