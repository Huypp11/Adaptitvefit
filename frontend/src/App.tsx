import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { PublicOnly, RequireAuth, RequireRole } from './auth/guards';
import AppLayout from './layouts/AppLayout';
import Login from './pages/auth/Login';
import Register from './pages/auth/Register';
import Dashboard from './pages/user/Dashboard';
import Profile from './pages/user/Profile';
import CheckIn from './pages/user/CheckIn';
import Exercises from './pages/user/Exercises';
import Workouts from './pages/user/Workouts';
import WorkoutDetail from './pages/user/WorkoutDetail';
import WorkoutPlayer from './pages/user/WorkoutPlayer';
import Progress from './pages/user/Progress';
import Today from './pages/user/Today';
import AdminExercises from './pages/admin/AdminExercises';
import AdminInterventions from './pages/admin/AdminInterventions';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<PublicOnly><Login /></PublicOnly>} />
          <Route path="/register" element={<PublicOnly><Register /></PublicOnly>} />

          <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
            <Route element={<RequireRole role="USER" />}>
              <Route path="/" element={<Dashboard />} />
              <Route path="/profile" element={<Profile />} />
              <Route path="/checkin" element={<CheckIn />} />
              <Route path="/today" element={<Today />} />
              <Route path="/exercises" element={<Exercises />} />
              <Route path="/workouts" element={<Workouts />} />
              <Route path="/workouts/:id" element={<WorkoutDetail />} />
              <Route path="/workouts/:id/play" element={<WorkoutPlayer />} />
              <Route path="/progress" element={<Progress />} />
            </Route>
            <Route element={<RequireRole role="ADMIN" />}>
              <Route path="/admin/exercises" element={<AdminExercises />} />
              <Route path="/admin/interventions" element={<AdminInterventions />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
