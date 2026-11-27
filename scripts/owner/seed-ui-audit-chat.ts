import * as fs from "../../supabase/functions/_shared/firestore.ts";
import { bearer, emulatorToken } from "../../supabase/functions/_shared/testing.ts";
import { handleChatAction } from "../../supabase/functions/chat-action/handler.ts";
import { handleChatSend } from "../../supabase/functions/chat-send/handler.ts";

if (fs.projectId() !== "demo-admin-repair" ||
    Deno.env.get("FIRESTORE_EMULATOR_HOST") !== "127.0.0.1:18080" ||
    Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST") !== "127.0.0.1:19099") {
  throw new Error("Owner UI fixtures require the isolated local project");
}
const player = "feature11-player";
await fs.commit([fs.updateWrite(`users/${player}`, {
  displayName: "Nguyễn Hoàng Minh Anh và nhóm cầu lông cuối tuần",
})]);
const request = (body: object) => bearer(emulatorToken({ sub: player, appRole: "PLAYER" }), { body: JSON.stringify(body) });
const opened = await handleChatAction(request({ action: "openDirect", venueId: "feature11-b" }));
if (!opened.ok) throw new Error(`Opening demo conversation: ${opened.status} ${await opened.text()}`);
const { chatId } = await opened.json();
const sent = await handleChatSend(request({ chatId, messageId: crypto.randomUUID(),
  text: "Tôi muốn hỏi lịch trống cho buổi cầu lông cuối tuần này." }));
if (!sent.ok) throw new Error(`Demo message: ${sent.status} ${await sent.text()}`);
await sent.body?.cancel();
console.log("Demo chat and message ready in Firebase Emulator.");
