import type { ReactNode } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';
import type { Role } from '../api/types';

export const homeFor = (role: Role) => (role === 'ADMIN' ? '/admin/exercises' : '/');

export function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user ? <>{children}</> : <Navigate to="/login" replace />;
}

export function RequireRole({ role }: { role: Role }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return user.role === role ? <Outlet /> : <Navigate to={homeFor(user.role)} replace />;
}

export function PublicOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user ? <Navigate to={homeFor(user.role)} replace /> : <>{children}</>;
}
