// Kiểm tra key và model của Gemini/Groq bằng đúng prompt + JSON Schema mà Edge Function dùng.
// Không ghi Firestore, không trừ lượt. Chạy từ supabase/functions:
//   read -s GEMINI_API_KEY && export GEMINI_API_KEY
//   read -s GROQ_API_KEY && export GROQ_API_KEY
//   GEMINI_MODEL=<model> deno run --allow-env --allow-net ai-search-parse/check-providers.ts
import { parsePrompt, parseResult, parseSchema } from "./criteria.ts";
import { ProviderError, providersFromEnv } from "./llm.ts";

const catalog = {
  districts: { q10: "Quận 10", "tan-binh": "Tân Bình" },
  amenities: { parking: "Gửi xe" },
};
const query = "Tối mai 7h-9h Quận 10 dưới 100k có gửi xe, sân ít trơn";
const now = new Date();
const providers = providersFromEnv();
if (providers.length === 0) {
  console.error("Chưa có GEMINI_API_KEY hoặc GROQ_API_KEY trong môi trường.");
  Deno.exit(1);
}
let failed = false;
for (const provider of providers) {
  const model = provider.name.startsWith("gemini")
    ? Deno.env.get("GEMINI_MODEL") ?? "(mặc định trong llm.ts)"
    : Deno.env.get("GROQ_MODEL") ?? "(mặc định trong llm.ts)";
  const started = performance.now();
  try {
    const raw = await provider.generate(
      parsePrompt(catalog, now),
      JSON.stringify({ query }),
      parseSchema,
      10_000,
    );
    const result = parseResult(raw, query, catalog, now);
    console.log(
      `OK   ${provider.name} [${model}] ${
        Math.round(performance.now() - started)
      } ms`,
    );
    console.log(`     intent=${result.intent} preference=${result.preference}`);
    console.log(`     filter=${JSON.stringify(result.filter)}`);
  } catch (error) {
    failed = true;
    const status = error instanceof ProviderError ? error.status : "?";
    console.log(
      `FAIL ${provider.name} [${model}] status=${status} (xem dòng cảnh báo phía trên)`,
    );
  }
}
Deno.exit(failed ? 1 : 0);
