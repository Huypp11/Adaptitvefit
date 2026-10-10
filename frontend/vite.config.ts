import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Frontend goi /api/... va Vite chuyen tiep sang Spring Boot, nen khong vuong CORS khi dev.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_BACKEND_URL ?? 'http://localhost:8080', changeOrigin: true },
    },
  },
});
