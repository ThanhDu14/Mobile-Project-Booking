// Phần lõi của backfill-group-chats.ts, tách ra để test trên Emulator.
import { groupChatId, syncGroupChat } from "../../supabase/functions/_shared/chats.ts";
import { get, query, runTransaction } from "../../supabase/functions/_shared/firestore.ts";

/** 1 write chat + 1 write mỗi participant phải nằm trong giới hạn 500 write của một transaction. */
export const MAX_MEMBERS = 450;

export interface BackfillResult {
  scanned: number;
  existing: number;
  skippedDeleted: number;
  /** groupId đã tạo (hoặc sẽ tạo khi dry-run) chat. */
  created: string[];
  failed: { groupId: string; error: string }[];
}

type Outcome = "created" | "existing" | "deleted";

export async function backfillGroupChats(opts: { dryRun?: boolean } = {}): Promise<BackfillResult> {
  // shortcut: runQuery một lượt không phân trang; cần phân trang theo __name__ nếu có hàng chục nghìn nhóm.
  const groups = await query("groups", [], { select: ["status"] });
  const result: BackfillResult = { scanned: groups.length, existing: 0, skippedDeleted: 0, created: [], failed: [] };
  for (const g of groups) {
    try {
      if (g.data.status === "DELETED") {
        result.skippedDeleted++;
        continue;
      }
      if (await get(`chats/${groupChatId(g.id)}`)) {
        result.existing++;
        continue;
      }
      if (opts.dryRun) {
        result.created.push(g.id);
        continue;
      }
      const outcome = await runTransaction<Outcome>(async (tx) => {
        // Đọc lại trong transaction: nhóm có thể vừa bị xóa, hoặc group-manage vừa tạo chat.
        const group = await tx.get(`groups/${g.id}`);
        if (!group || group.status === "DELETED") return "deleted";
        if (await tx.get(`chats/${groupChatId(g.id)}`)) return "existing";
        const members = await tx.query("members", [], { parent: `groups/${g.id}` });
        if (members.length > MAX_MEMBERS) throw new Error(`${members.length} thành viên > ${MAX_MEMBERS}`);
        await syncGroupChat(tx, g.id, { created: true, added: members.map((m) => m.id) });
        return "created";
      });
      if (outcome === "created") result.created.push(g.id);
      else if (outcome === "existing") result.existing++;
      else result.skippedDeleted++;
    } catch (e) {
      result.failed.push({ groupId: g.id, error: (e as Error).message });
    }
  }
  return result;
}
