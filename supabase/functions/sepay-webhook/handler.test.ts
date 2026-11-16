import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleSepayWebhook } from "./handler.ts";
import { hmacSha256Hex } from "../_shared/sepay.ts";

Deno.env.set("FIREBASE_PROJECT_ID", "demo-smashnow");

const SECRET = "test-sepay-secret";
const NOW = 1_763_000_000;
// T013: mã đổi sang tiền tố SNP. Handler vẫn là bản nạp ví cũ cho tới T042 (US4).
const CODE = "SNPABCD2345";

function payload(overrides: Record<string, unknown> = {}) {
  return {
    id: 92704,
    gateway: "Vietcombank",
    transactionDate: "2026-11-13 11:08:33",
    accountNumber: "0123456789",
    subAccount: "",
    code: CODE,
    content: `${CODE} nap vi`,
    transferType: "in",
    description: "",
    transferAmount: 100000,
    accumulated: 0,
    referenceCode: "SB123",
    ...overrides,
  };
}

async function signedRequest(body: unknown, opts: { secret?: string; timestamp?: number } = {}) {
  const raw = JSON.stringify(body);
  const ts = String(opts.timestamp ?? NOW);
  const sig = "sha256=" + await hmacSha256Hex(opts.secret ?? SECRET, `${ts}.${raw}`);
  return new Request("https://localhost/sepay-webhook", {
    method: "POST",
    headers: { "content-type": "application/json", "x-sepay-signature": sig, "x-sepay-timestamp": ts },
    body: raw,
  });
}

/** Firestore giả: lưu document trong Map, chạy transaction một lần và áp các write đã trả về. */
function fakeFirestore(docs: Record<string, Record<string, unknown>>) {
  const store = new Map(Object.entries(docs));
  const writes: any[] = [];
  const firestore = {
    runTransaction: async (operation: (tx: any) => Promise<any>) => {
      const out = await operation({ get: async (path: string) => store.get(path) ?? null });
      for (const w of out.writes) {
        writes.push(w);
        const path = String(w.update.name).split("/documents/")[1];
        const fields: Record<string, unknown> = {};
        for (const [k, v] of Object.entries(w.update.fields as Record<string, any>)) {
          fields[k] = v.integerValue !== undefined ? Number(v.integerValue) : v.stringValue ?? v.nullValue ?? v;
        }
        store.set(path, { ...(store.get(path) ?? {}), ...fields });
      }
      return out.result;
    },
  } as any;
  return { firestore, store, writes };
}

function pendingTopup() {
  return {
    [`walletTopups/${CODE}`]: { code: CODE, userId: "u1", amount: 100000, status: "PENDING" },
    "users/u1": { walletBalance: 50000 },
  };
}

const deps = (firestore: any) => ({ firestore, secret: SECRET, nowSeconds: () => NOW });

// Test thuần của _shared/sepay.ts đã chuyển sang _shared/sepay.test.ts (T008).

Deno.test("sepay-webhook: sai chữ ký hoặc sai secret → 401, không đụng Firestore", async () => {
  const { firestore, writes } = fakeFirestore(pendingTopup());
  const res = await handleSepayWebhook(await signedRequest(payload(), { secret: "wrong" }), deps(firestore));
  assertEquals(res.status, 401);
  assertEquals(writes.length, 0);
  const old = await handleSepayWebhook(await signedRequest(payload(), { timestamp: NOW - 600 }), deps(firestore));
  assertEquals(old.status, 401);
});

Deno.test("sepay-webhook: thiếu secret cấu hình → 500 NOT_CONFIGURED", async () => {
  const { firestore } = fakeFirestore({});
  const res = await handleSepayWebhook(await signedRequest(payload()), { ...deps(firestore), secret: "" });
  assertEquals(res.status, 500);
});

Deno.test("sepay-webhook: giao dịch khớp → cộng ví, ghi lịch sử, topup SUCCESS, trả success true", async () => {
  const { firestore, store } = fakeFirestore(pendingTopup());
  const res = await handleSepayWebhook(await signedRequest(payload()), deps(firestore));
  assertEquals(res.status, 200);
  assertEquals((await res.json()).success, true);
  assertEquals(store.get("users/u1")?.walletBalance, 150000);
  assertEquals(store.get(`walletTopups/${CODE}`)?.status, "SUCCESS");
  assertEquals(store.get("users/u1/walletTransactions/sepay-92704")?.balanceAfter, 150000);
  assertEquals(store.get("sepayTransactions/92704")?.outcome, "CREDITED");
});

Deno.test("sepay-webhook: SePay gửi lại cùng id → chỉ cộng ví một lần", async () => {
  const { firestore, store } = fakeFirestore(pendingTopup());
  for (let i = 0; i < 3; i++) {
    const res = await handleSepayWebhook(await signedRequest(payload()), deps(firestore));
    assertEquals(res.status, 200);
  }
  assertEquals(store.get("users/u1")?.walletBalance, 150000);
});

Deno.test("sepay-webhook: sai số tiền, không có mã, tiền ra → 200 nhưng không cộng ví", async () => {
  const cases: [Record<string, unknown>, string][] = [
    [{ transferAmount: 99000 }, "AMOUNT_MISMATCH"],
    [{ id: 2, code: null, content: "chuyen tien" }, "NO_CODE"],
    [{ id: 3, transferType: "out" }, "NOT_INCOMING"],
    [{ id: 4, code: "SNPZZZZ2222", content: "" }, "TOPUP_NOT_FOUND"],
  ];
  for (const [override, expected] of cases) {
    const { firestore, store } = fakeFirestore(pendingTopup());
    const res = await handleSepayWebhook(await signedRequest(payload(override)), deps(firestore));
    assertEquals(res.status, 200);
    assertEquals((await res.json()).outcome, expected);
    assertEquals(store.get("users/u1")?.walletBalance, 50000);
  }
});

Deno.test("sepay-webhook: code null nhưng nội dung có mã → vẫn khớp", async () => {
  const { firestore, store } = fakeFirestore(pendingTopup());
  const res = await handleSepayWebhook(
    await signedRequest(payload({ code: null, content: `IBFT ${CODE} FT2611` })),
    deps(firestore),
  );
  assertEquals((await res.json()).outcome, "CREDITED");
  assertEquals(store.get("users/u1")?.walletBalance, 150000);
});

Deno.test("sepay-webhook: topup đã SUCCESS (giao dịch thứ hai cùng mã) → không cộng thêm", async () => {
  const docs = pendingTopup();
  docs[`walletTopups/${CODE}`].status = "SUCCESS";
  const { firestore, store } = fakeFirestore(docs);
  const res = await handleSepayWebhook(await signedRequest(payload({ id: 555 })), deps(firestore));
  assertEquals((await res.json()).outcome, "TOPUP_NOT_PENDING");
  assertEquals(store.get("users/u1")?.walletBalance, 50000);
});

Deno.test("sepay-webhook: Firestore lỗi → 500 để SePay thử lại", async () => {
  const firestore = { runTransaction: async () => { throw new Error("boom"); } } as any;
  const res = await handleSepayWebhook(await signedRequest(payload()), deps(firestore));
  assertEquals(res.status, 500);
});
