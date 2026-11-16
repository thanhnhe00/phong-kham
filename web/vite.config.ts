/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Khi dev, /api chuyển tiếp sang backend Spring Boot (đổi bằng biến API_TARGET)
const proxy = { '/api': process.env.API_TARGET ?? 'http://localhost:8080' };

export default defineConfig({
  plugins: [react()],
  server: { proxy },
  preview: { proxy },
  test: { include: ['src/**/*.test.ts'] },
});
