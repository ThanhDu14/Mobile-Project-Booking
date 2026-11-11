import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // Các file test dùng chung một Emulator, chạy tuần tự để không xóa dữ liệu của nhau.
    fileParallelism: false,
    testTimeout: 15000,
    // CI đặt SKIP_BLOCKED=1 để bỏ qua ca có tên chứa "[BLOCKED: X]" (đang chờ thành viên X sửa
    // Rules). Chạy `npm test` trên máy không đặt biến này nên vẫn thấy đủ các ca đó.
    ...(process.env.SKIP_BLOCKED ? { testNamePattern: /^(?!.*\[BLOCKED)/ } : {}),
  },
});
