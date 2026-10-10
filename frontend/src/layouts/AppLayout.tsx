import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const userLinks = [
  { to: '/', label: 'Tổng quan', end: true },
  { to: '/checkin', label: 'Check-in hằng ngày' },
  { to: '/exercises', label: 'Thư viện bài tập' },
  { to: '/workouts', label: 'Buổi tập của tôi' },
  { to: '/progress', label: 'Tiến độ' },
  { to: '/profile', label: 'Hồ sơ' },
];

const adminLinks = [{ to: '/admin/exercises', label: 'Quản lý bài tập', end: false }];

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const links = user?.role === 'ADMIN' ? adminLinks : userLinks;

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">Adaptive<span>Fit</span></div>
        <nav className="nav" aria-label="Điều hướng chính">
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.end}>{l.label}</NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <span>{user?.email}{user?.role === 'ADMIN' ? ' (admin)' : ''}</span>
          <button className="btn btn-quiet" style={{ color: '#e9f1ee', textAlign: 'left', padding: 0 }}
                  onClick={() => { logout(); navigate('/login'); }}>
            Đăng xuất
          </button>
        </div>
      </aside>
      <main className="main"><Outlet /></main>
    </div>
  );
}
