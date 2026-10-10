import axios, { AxiosError } from 'axios';

export const TOKEN_KEY = 'af_token';
export const USER_KEY = 'af_user';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((cfg) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});

api.interceptors.response.use(
  (res) => res,
  (err: AxiosError) => {
    const url = err.config?.url ?? '';
    // Token het han hoac khong hop le: xoa phien va ve trang dang nhap (tru chinh cac API auth)
    if (err.response?.status === 401 && !url.startsWith('/auth/')) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      if (window.location.pathname !== '/login') window.location.assign('/login');
    }
    return Promise.reject(err);
  },
);

export default api;
