// Test backfill chat nhóm trên Firebase Emulator (Firestore 8080):
//   deno test -A --config supabase/functions/deno.json scripts/admin/backfill-group-chats.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { assertChatInvariant, ensureUsers, seedGroupChat } from "../../supabase/functions/_shared/chats_fixtures.ts";
import { commit, createWrite, get } from "../../supabase/functions/_shared/firestore.ts";
import { clearEmulators } from "../../supabase/functions/_shared/testing.ts";
import { backfillGroupChats } from "./backfill-group-chats-lib.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

async function seedGroup(id: string, status: string, memberIds: string[]) {
  await commit([
    createWrite(`groups/${id}`, { name: id, status, memberCount: memberIds.length }),
    ...memberIds.map((u) => createWrite(`groups/${id}/members/${u}`, { userId: u, role: "MEMBER" })),
  ]);
}

Deno.test("backfill: tạo chat cho nhóm cũ (ACTIVE, LOCKED), bỏ qua nhóm DELETED và nhóm đã có chat; chạy lại không đổi", async () => {
  await clearEmulators();
  await ensureUsers(["u1", "u2", "u3"]);
  await seedGroup("old", "ACTIVE", ["u1", "u2"]);
  await seedGroup("locked", "LOCKED", ["u3"]);
  await seedGroup("gone", "DELETED", ["u1"]);
  await seedGroup("new", "ACTIVE", ["u1", "u3"]);
  await seedGroupChat("new", ["u1", "u3"]);
  await seedGroup("empty", "ACTIVE", []);

  const dry = await backfillGroupChats({ dryRun: true });
  assertEquals(dry.created.sort(), ["empty", "locked", "old"]);
  assertEquals(await get("chats/group_old"), null, "dry-run không được ghi");

  const r = await backfillGroupChats();
  assertEquals(r, { scanned: 5, existing: 1, skippedDeleted: 1, created: r.created, failed: [] });
  assertEquals(r.created.sort(), ["empty", "locked", "old"]);
  // Người ở nhóm lâu không bị tính chưa đọc: lastReadAt = joinedAt = lúc tạo, unreadCounts = 0.
  const p = await get("chats/group_old/participants/u1");
  assert(p?.lastReadAt && p?.joinedAt);
  assertEquals((await get("chats/group_old"))?.unreadCounts, { u1: 0, u2: 0 });
  await assertChatInvariant("group_old");
  await assertChatInvariant("group_locked");
  const chat = await get("chats/group_old");
  assert(chat);
  assertEquals((chat.memberIds as string[]).sort(), ["u1", "u2"]);
  assertEquals(chat.status, "ACTIVE");
  assertEquals(await get("chats/group_gone"), null);

  const again = await backfillGroupChats();
  assertEquals(again.created, []);
  assertEquals(again.existing, 4);
});

Deno.test("backfill: thành viên đã xóa tài khoản (không có users/{uid}) vẫn tạo được, snapshot rỗng", async () => {
  await clearEmulators();
  await ensureUsers(["u1"]);
  await seedGroup("g", "ACTIVE", ["u1", "ghost"]);
  const r = await backfillGroupChats();
  assertEquals(r.created, ["g"]);
  await assertChatInvariant("group_g");
  const ghost = await get("chats/group_g/participants/ghost");
  assertEquals((ghost?.userSnapshot as { displayName: unknown } | undefined)?.displayName ?? null, null);
});
