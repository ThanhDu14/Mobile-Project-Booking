// Dữ liệu mẫu dùng chung cho test của spec 011 (A).
import { Timestamp } from 'firebase/firestore';

/** Document `users/{uid}` như on-signup tạo (010 data-model §2). */
export function seedUser(uid: string, overrides: Record<string, unknown> = {}) {
  return {
    displayName: 'Lê Hoàng Nam',
    email: `${uid}@example.com`,
    phone: null,
    avatarPath: null,
    level: null,
    favoriteDistrictIds: [],
    role: 'PLAYER',
    ownerStatus: 'NONE',
    accountStatus: 'ACTIVE',
    fcmTokens: [],
    notificationPrefs: {},
    walletBalance: 0,
    termsAcceptedAt: Timestamp.fromMillis(1_760_000_000_000),
    createdAt: Timestamp.fromMillis(1_760_000_000_000),
    updatedAt: Timestamp.fromMillis(1_760_000_000_000),
    ...overrides,
  };
}

/** `districts/{id}` theo 011 data-model §3. `q-cu` đã ngừng (chỉ có trong dữ liệu test). */
export function seedDistrict(name: string, sortOrder: number, isActive = true) {
  return { name, cityName: 'TP. Hồ Chí Minh', sortOrder, isActive };
}
