import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing';

// Tiền tố `demo-` bảo đảm chỉ chạy trên Emulator, không bao giờ chạm project thật
// (constitution, nguyên tắc IV).
export const PROJECT_ID = 'demo-smashnow';

export function createTestEnv(): Promise<RulesTestEnvironment> {
  const rules = readFileSync(fileURLToPath(new URL('../../firestore.rules', import.meta.url)), 'utf8');
  return initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules },
  });
}
