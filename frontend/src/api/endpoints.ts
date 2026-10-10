import api from './client';
import type {
  AuthResponse, Checkin, CheckinInput, Exercise, ExerciseInput, FeedbackInput,
  PageResponse, Profile, Workout, WorkoutSummary,
} from './types';

export const authApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>('/auth/login', { email, password }).then((r) => r.data),
  register: (email: string, password: string) =>
    api.post<AuthResponse>('/auth/register', { email, password }).then((r) => r.data),
};

export const profileApi = {
  get: () => api.get<Profile>('/profile').then((r) => r.data),
  save: (p: Profile) => api.put<Profile>('/profile', p).then((r) => r.data),
};

export const checkinApi = {
  create: (c: CheckinInput) => api.post<Checkin>('/checkins', c).then((r) => r.data),
  today: () => api.get<Checkin>('/checkins/today').then((r) => r.data),
  list: (from?: string, to?: string) =>
    api.get<Checkin[]>('/checkins', { params: { from, to } }).then((r) => r.data),
};

export interface ExerciseFilter {
  q?: string; tag?: string; location?: string; intensity?: string; active?: boolean;
  page?: number; size?: number;
}

export const exerciseApi = {
  list: (f: ExerciseFilter) =>
    api.get<PageResponse<Exercise>>('/exercises', { params: f }).then((r) => r.data),
};

export const adminExerciseApi = {
  list: (f: ExerciseFilter) =>
    api.get<PageResponse<Exercise>>('/admin/exercises', { params: f }).then((r) => r.data),
  create: (e: ExerciseInput) => api.post<Exercise>('/admin/exercises', e).then((r) => r.data),
  update: (id: number, e: ExerciseInput) =>
    api.put<Exercise>(`/admin/exercises/${id}`, e).then((r) => r.data),
  deactivate: (id: number) => api.delete(`/admin/exercises/${id}`).then(() => undefined),
};

export const workoutApi = {
  start: (exerciseIds: number[]) =>
    api.post<Workout>('/workouts/start', { exerciseIds }).then((r) => r.data),
  get: (id: number) => api.get<Workout>(`/workouts/${id}`).then((r) => r.data),
  complete: (id: number) => api.post<Workout>(`/workouts/${id}/complete`).then((r) => r.data),
  abandon: (id: number) => api.post<Workout>(`/workouts/${id}/abandon`).then((r) => r.data),
  history: (page = 0, size = 20) =>
    api.get<PageResponse<WorkoutSummary>>('/workouts', { params: { page, size } }).then((r) => r.data),
};

export const feedbackApi = {
  create: (f: FeedbackInput) => api.post('/feedback', f).then((r) => r.data),
};
