// Bộ đánh giá câu mẫu cho "Tìm bằng AI" (spec 022: FR-024, SC-001, SC-002, SC-007; contracts/eval-suite.md).
//
// Gọi thẳng handleAiSearch (action PARSE) trong cùng tiến trình với:
//   - Firestore giả trong bộ nhớ (không đọc/ghi project thật, không tốn lượt AI thật),
//   - danh mục quận/tiện ích từ scripts/seed/catalog.json,
//   - mỗi câu một người dùng giả mới, giờ cố định theo cases.json,
//   - nhà cung cấp LLM THẬT lấy từ biến môi trường (đây là thứ cần đo).
//
// Chạy từ thư mục gốc repo (không dán key vào lệnh hay chat):
//   read -s GEMINI_API_KEY && export GEMINI_API_KEY
//   GEMINI_MODEL=gemini-3.5-flash-lite deno run --allow-env --allow-net --allow-read --allow-write \
//     scripts/ai-eval/run-eval.ts --out docs/reports/ai-eval
// Tùy chọn: --cases <file>, --only S01,E02, --delay-ms 1500, --dry-run (LLM giả, chỉ kiểm tra script),
//   --budget-ms 20000 (thời gian chờ LLM cho eval; production giữ 6 s cho lần gọi đầu, 8,5 s tổng).

const args = parseArgs(Deno.args);
// documentName() của _shared/firestore.ts cần project id để tạo object ghi; bản giả không gửi gì đi.
if (!Deno.env.get("FIREBASE_PROJECT_ID")) {
  Deno.env.set("FIREBASE_PROJECT_ID", "ai-eval-local");
}
const { handleAiSearch, version } = await import(
  "../../supabase/functions/ai-search-parse/handler.ts"
);
const { providersFromEnv } = await import(
  "../../supabase/functions/ai-search-parse/llm.ts"
);
type Deps = Parameters<typeof handleAiSearch>[1];
type Provider = ReturnType<typeof providersFromEnv>[number];

type Expect = {
  intent: string;
  filter?: Record<string, unknown>;
  dropped?: string[];
  faqTopic?: string | null;
  preference?: string | null;
};
type Case = {
  id: string;
  group: "search" | "exception";
  query: string;
  now: string | null;
  expect: Expect;
  mustNotContain: string[];
};

const FILTER_KEYS = [
  "type",
  "keyword",
  "districtId",
  "date",
  "dateChoices",
  "start",
  "end",
  "priceMin",
  "priceMax",
  "amenityIds",
  "sort",
  "level",
] as const;
const ARRAY_KEYS = new Set(["dateChoices", "amenityIds"]);
const RESULT_KEYS = new Set([
  "intent",
  "faqTopic",
  "filter",
  "preference",
  "dropped",
]);
const ALLOWED_FILTER_KEYS = new Set<string>([...FILTER_KEYS, "partOfDay"]);
const RESPONSE_KEYS = new Set(["result", "cached", "usage"]);

const casesFile = args.cases ?? "scripts/ai-eval/cases.json";
const suite = JSON.parse(await Deno.readTextFile(casesFile)) as {
  today: string;
  cases: Case[];
};
const only = args.only ? new Set(args.only.split(",")) : null;
const cases = suite.cases.filter((item) => !only || only.has(item.id));
const delayMs = Number(args["delay-ms"] ?? 1500);
const dryRun = args["dry-run"] === "true";
// Đo chất lượng câu trả lời, không đo giới hạn thời gian: mạng của người chạy có thể chậm hơn Supabase.
const budgetMs = Number(args["budget-ms"] ?? 20_000);

const catalog = JSON.parse(
  await Deno.readTextFile(new URL("../seed/catalog.json", import.meta.url)),
) as Record<"districts" | "amenities", Array<{ id: string; name: string }>>;

const baseProviders: Provider[] = dryRun
  ? [stubProvider()]
  : providersFromEnv();
if (baseProviders.length === 0) {
  console.error(
    "Chưa có GEMINI_API_KEY/GEMINI_API_KEY_2/GROQ_API_KEY trong môi trường (hoặc dùng --dry-run).",
  );
  Deno.exit(2);
}

type Row = {
  id: string;
  group: string;
  query: string;
  status: number;
  intent: string;
  intentOk: boolean;
  fullOk: boolean;
  diffs: string[];
  freeTextViolations: string[];
  piiViolations: string[];
  providers: string[];
  llmMs: number;
  /** Kết quả `result` (hoặc mã lỗi) trả về, để chẩn đoán câu sai. Không chứa câu gốc ngoài `keyword`. */
  result: unknown;
};
const rows: Row[] = [];

for (const [index, item] of cases.entries()) {
  if (index > 0 && !dryRun) await sleep(delayMs);
  const llmInputs: string[] = [];
  const calls: Array<{ provider: string; ms: number }> = [];
  const writes: unknown[] = [];
  const providers = baseProviders.map((provider): Provider => ({
    name: provider.name,
    async generate(prompt, input, schema, timeoutMs) {
      llmInputs.push(`${prompt}\n${input}`);
      const started = performance.now();
      try {
        return await provider.generate(prompt, input, schema, timeoutMs);
      } finally {
        calls.push({
          provider: provider.name,
          ms: Math.round(performance.now() - started),
        });
      }
    },
  }));
  const deps = {
    db: memoryDb(writes),
    authenticate: () => Promise.resolve({ uid: `eval-${item.id}` }),
    providers,
    now: () => new Date(item.now ?? suite.today),
    // Same split as production (6 s of 8.5 s), so a hung first provider still leaves time for a fallback.
    timeouts: { totalMs: budgetMs, firstMs: Math.round(budgetMs * 6 / 8.5) },
  } as unknown as Deps;
  const response = await handleAiSearch(
    new Request("http://eval.local/ai-search-parse", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ action: "PARSE", query: item.query }),
    }),
    deps,
  );
  const body = await response.json().catch(() => ({})) as Record<
    string,
    unknown
  >;
  rows.push(
    score(item, response.status, body, llmInputs, writes, calls),
  );
  const row = rows.at(-1)!;
  console.log(
    `${
      row.fullOk ? "OK  " : row.intentOk ? "PART" : "FAIL"
    } ${item.id} ${row.intent} [${
      row.providers.join(">") || "-"
    } ${row.llmMs}ms]` +
      (row.diffs.length ? `  ${row.diffs.join("; ")}` : ""),
  );
}

const report = summarize(rows);
const outDir = args.out ?? "docs/reports/ai-eval";
await Deno.mkdir(outDir, { recursive: true });
const stamp = new Date().toISOString().slice(0, 10);
const name = `${outDir}/${stamp}-${version}${dryRun ? "-dry-run" : ""}`;
await Deno.writeTextFile(
  `${name}.json`,
  JSON.stringify({ version, models: modelInfo(), report, rows }, null, 2),
);
await Deno.writeTextFile(`${name}.md`, markdown(report, rows));
console.log(`\n${markdownSummary(report)}\nĐã ghi ${name}.md và .json`);
Deno.exit(report.allTargetsMet ? 0 : 1);

// ---------------------------------------------------------------------------

function score(
  item: Case,
  status: number,
  body: Record<string, unknown>,
  llmInputs: string[],
  writes: unknown[],
  calls: Array<{ provider: string; ms: number }>,
): Row {
  const error = body.error as { code?: string } | undefined;
  const result = (body.result ?? {}) as Record<string, unknown>;
  const intent = status === 200
    ? String(result.intent ?? "")
    : error?.code ?? `HTTP_${status}`;
  const expect = item.expect;
  const diffs: string[] = [];
  const intentOk = intent === expect.intent;
  if (!intentOk) diffs.push(`intent ${intent} ≠ ${expect.intent}`);

  if (
    status === 200 &&
    (expect.intent === "search" || expect.intent === "unclear")
  ) {
    const filter = (result.filter ?? {}) as Record<string, unknown>;
    for (const key of FILTER_KEYS) {
      const wanted = expect.filter?.[key] ??
        (key === "type" ? "COURT" : ARRAY_KEYS.has(key) ? [] : null);
      const actual = filter[key] ??
        (ARRAY_KEYS.has(key) ? [] : null);
      if (!same(key, wanted, actual)) {
        diffs.push(`${key} ${show(actual)} ≠ ${show(wanted)}`);
      }
    }
  }
  if (status === 200) {
    const wantedPreference = expect.preference ?? null;
    if ((result.preference ?? null) !== wantedPreference) {
      diffs.push(
        `preference ${show(result.preference)} ≠ ${show(wantedPreference)}`,
      );
    }
    if (expect.intent === "faq") {
      const wantedTopic = expect.faqTopic ?? null;
      if ((result.faqTopic ?? null) !== wantedTopic) {
        diffs.push(`faqTopic ${show(result.faqTopic)} ≠ ${show(wantedTopic)}`);
      }
    }
    if (expect.dropped) {
      const dropped = Array.isArray(result.dropped) ? result.dropped : [];
      if (!sameSet(dropped, expect.dropped)) {
        diffs.push(`dropped ${show(dropped)} ≠ ${show(expect.dropped)}`);
      }
    }
  }

  // SC-002: không có trường ngoài contract, keyword phải là chuỗi con của câu.
  const freeText: string[] = [];
  if (status === 200) {
    for (const key of Object.keys(body)) {
      if (!RESPONSE_KEYS.has(key)) freeText.push(`response.${key}`);
    }
    for (const key of Object.keys(result)) {
      if (!RESULT_KEYS.has(key)) freeText.push(`result.${key}`);
    }
    const filter = result.filter as Record<string, unknown> | null;
    for (const key of Object.keys(filter ?? {})) {
      if (!ALLOWED_FILTER_KEYS.has(key)) freeText.push(`filter.${key}`);
    }
    const keyword = filter?.keyword;
    if (
      typeof keyword === "string" &&
      !fold(item.query).includes(fold(keyword))
    ) freeText.push(`keyword "${keyword}" không có trong câu`);
  }

  // SC-007: chuỗi cấm không được vào input LLM, response hay bản ghi giả (aiMetrics, cache, aiUsage).
  const pii: string[] = [];
  for (const secret of item.mustNotContain) {
    if (llmInputs.some((text) => text.includes(secret))) {
      pii.push(`LLM input: ${secret}`);
    }
    if (JSON.stringify(body).includes(secret)) pii.push(`response: ${secret}`);
    if (JSON.stringify(writes).includes(secret)) {
      pii.push(`Firestore writes: ${secret}`);
    }
  }

  return {
    id: item.id,
    group: item.group,
    query: item.query,
    status,
    intent,
    intentOk,
    fullOk: diffs.length === 0,
    diffs,
    freeTextViolations: freeText,
    piiViolations: pii,
    providers: calls.map(({ provider }) => provider),
    llmMs: calls.reduce((total, { ms }) => total + ms, 0),
    result: status === 200 ? body.result : body.error,
  };
}

function summarize(rows: Row[]) {
  const search = rows.filter((row) => row.group === "search");
  const exception = rows.filter((row) => row.group === "exception");
  const rate = (part: Row[], ok: (row: Row) => boolean) =>
    part.length ? part.filter(ok).length / part.length : 1;
  const latencies = rows.filter((row) => row.llmMs > 0).map((row) => row.llmMs)
    .sort((a, b) => a - b);
  const percentile = (p: number) =>
    latencies.length
      ? latencies[
        Math.min(latencies.length - 1, Math.floor(p * latencies.length))
      ]
      : 0;
  const intentRate = rate(search, (row) => row.intentOk);
  const fullRate = rate(search, (row) => row.fullOk);
  const branchRate = rate(exception, (row) => row.intentOk);
  const freeText = rows.reduce(
    (n, row) => n + row.freeTextViolations.length,
    0,
  );
  const pii = rows.reduce((n, row) => n + row.piiViolations.length, 0);
  const fallbacks = rows.filter((row) => row.providers.length > 1).length;
  return {
    total: rows.length,
    search: search.length,
    exception: exception.length,
    intentRate,
    fullRate,
    branchRate,
    freeText,
    pii,
    fallbacks,
    p50: percentile(0.5),
    p90: percentile(0.9),
    allTargetsMet: intentRate >= 0.95 && fullRate >= 0.85 &&
      branchRate === 1 && freeText === 0 && pii === 0,
  };
}

function markdownSummary(r: ReturnType<typeof summarize>): string {
  const pct = (value: number) => `${(value * 100).toFixed(1)}%`;
  const mark = (ok: boolean) => (ok ? "Đạt" : "Chưa đạt");
  return [
    "| Chỉ số | Kết quả | Mục tiêu | |",
    "|---|---|---|---|",
    `| Đúng ý định (tìm sân, ${r.search} câu) | ${
      pct(r.intentRate)
    } | ≥ 95% | ${mark(r.intentRate >= 0.95)} |`,
    `| Đúng toàn bộ điều kiện (tìm sân) | ${pct(r.fullRate)} | ≥ 85% | ${
      mark(r.fullRate >= 0.85)
    } |`,
    `| Đúng nhánh (ngoại lệ, ${r.exception} câu) | ${
      pct(r.branchRate)
    } | 100% | ${mark(r.branchRate === 1)} |`,
    `| Vi phạm văn bản tự do | ${r.freeText} | 0 | ${mark(r.freeText === 0)} |`,
    `| Vi phạm PII | ${r.pii} | 0 | ${mark(r.pii === 0)} |`,
    `| Độ trễ LLM p50 / p90 | ${r.p50} / ${r.p90} ms | tham khảo SC-004 | |`,
    `| Câu dùng nhà cung cấp dự phòng | ${r.fallbacks} | tham khảo | |`,
  ].join("\n");
}

function markdown(r: ReturnType<typeof summarize>, rows: Row[]): string {
  const failed = rows.filter((row) =>
    !row.fullOk || row.freeTextViolations.length || row.piiViolations.length
  );
  return [
    `# Đánh giá Tìm bằng AI — ${version}`,
    "",
    `Ngày chạy: ${new Date().toISOString()}. Mô hình: ${
      JSON.stringify(modelInfo())
    }. Bộ câu: \`${casesFile}\` (${r.total} câu, giờ giả định ${suite.today}).`,
    "",
    markdownSummary(r),
    "",
    "Số token không được nhà cung cấp trả qua lớp `LlmProvider`, nên chưa báo cáo.",
    "",
    "## Câu chưa khớp",
    "",
    failed.length
      ? [
        "| Câu | Nhóm | Ý định | Chênh lệch | Văn bản tự do / PII |",
        "|---|---|---|---|---|",
        ...failed.map((row) =>
          `| ${row.id} | ${row.group} | ${row.intent} | ${
            row.diffs.join("; ") || "—"
          } | ${
            [...row.freeTextViolations, ...row.piiViolations].join("; ") || "—"
          } |`
        ),
      ].join("\n")
      : "Không có.",
    "",
  ].join("\n");
}

function modelInfo() {
  return dryRun ? { provider: "stub" } : {
    budgetMs,
    gemini: Deno.env.get("GEMINI_MODEL") ?? "(mặc định trong llm.ts)",
    groq: Deno.env.get("GROQ_MODEL") ?? "(mặc định trong llm.ts)",
    providers: baseProviders.map(({ name }) => name),
  };
}

function memoryDb(writes: unknown[]) {
  const query = (collection: string) =>
    Promise.resolve(
      collection === "districts" || collection === "amenities"
        ? catalog[collection].map(({ id, name }) => ({
          id,
          path: `${collection}/${id}`,
          data: { name, isActive: true },
        }))
        : [],
    );
  return {
    get: () => Promise.resolve(null),
    query,
    commit: (list: unknown[]) => {
      writes.push(...list);
      return Promise.resolve();
    },
    runTransaction: async (
      operation: (tx: unknown) => Promise<unknown>,
    ) => {
      const tx = {
        get: () => Promise.resolve(null),
        query,
        write: (write: unknown) => writes.push(write),
      };
      const value = await operation(tx) as
        | { result: unknown; writes: unknown[] }
        | unknown;
      if (
        value && typeof value === "object" && "result" in value &&
        "writes" in value
      ) {
        writes.push(...(value as { writes: unknown[] }).writes);
        return (value as { result: unknown }).result;
      }
      return value;
    },
  };
}

function stubProvider(): Provider {
  return {
    name: "gemini",
    generate: () =>
      Promise.resolve({
        intent: "unclear",
        type: "COURT",
        venueName: null,
        districtId: null,
        dayKind: null,
        weekday: null,
        nextWeek: null,
        date: null,
        partOfDay: null,
        startTime: null,
        endTime: null,
        priceMin: null,
        priceMax: null,
        amenityIds: [],
        sort: null,
        level: null,
        faqTopic: null,
        preference: null,
      }),
  } as Provider;
}

function same(key: string, wanted: unknown, actual: unknown): boolean {
  if (ARRAY_KEYS.has(key)) {
    return sameSet(actual as unknown[], wanted as unknown[]);
  }
  if (key === "keyword" && typeof wanted === "string") {
    return typeof actual === "string" &&
      fold(actual).replace(/^san /, "") === fold(wanted).replace(/^san /, "");
  }
  return actual === wanted;
}

function sameSet(a: unknown[], b: unknown[]): boolean {
  const left = [...(a ?? [])].map(String).sort();
  const right = [...(b ?? [])].map(String).sort();
  return left.length === right.length && left.every((v, i) => v === right[i]);
}

/** So khớp không phân biệt hoa thường, dấu và khoảng trắng thừa. */
function fold(value: string): string {
  return value.normalize("NFD").replace(/\p{M}/gu, "").replace(/đ/gi, "d")
    .toLowerCase().replace(/\s+/g, " ").trim();
}

function show(value: unknown): string {
  return JSON.stringify(value ?? null);
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function parseArgs(list: string[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (let i = 0; i < list.length; i++) {
    const arg = list[i];
    if (!arg.startsWith("--")) continue;
    const key = arg.slice(2);
    const next = list[i + 1];
    if (next === undefined || next.startsWith("--")) out[key] = "true";
    else {
      out[key] = next;
      i++;
    }
  }
  return out;
}
