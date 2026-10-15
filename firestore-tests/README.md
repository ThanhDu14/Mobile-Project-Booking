# Test Firestore Rules

Test `firestore.rules` trên Firebase Emulator bằng `@firebase/rules-unit-testing` và Vitest. Không chạy trên project thật: project ID cố định là `demo-smashnow`.

Cần Node.js 20+ và Java 21+ (Emulator chạy trên JVM).

```bash
cd firestore-tests
npm install
npm test          # tự bật Firestore Emulator, chạy test, rồi tắt
```

Mỗi collection có một file `test/<collection>.test.ts` do người phụ trách viết (đặc tả CSDL mục 2). Mỗi dòng phân quyền ở mục 7 cần ít nhất một test "được phép" (`assertSucceeds`) và một test "bị chặn" (`assertFails`).

Chạy Emulator để thử bằng tay với app (Auth 9099, Firestore 8080, giao diện tại http://127.0.0.1:4000):

```bash
npx firebase-tools emulators:start --only auth,firestore --project demo-smashnow
```
