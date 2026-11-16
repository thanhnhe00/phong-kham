import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

// Kiểm thử đơn vị cho phần logic thuần trong src/lib (không cần thiết bị, không cần backend).
export default defineConfig({
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  test: { include: ['src/**/*.test.ts'] },
});
