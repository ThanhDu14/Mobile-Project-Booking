import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // Các file test dùng chung một Emulator, chạy tuần tự để không xóa dữ liệu của nhau.
    fileParallelism: false,
    testTimeout: 15000,
  },
});
