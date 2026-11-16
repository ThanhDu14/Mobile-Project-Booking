import { HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as firestore from "../_shared/firestore.ts";
import {
  addDays,
  isPreference,
  maskQuery,
  object,
  type Parsed,
  parsePrompt,
  parseResult,
  parseSchema,
  vnTime,
} from "./criteria.ts";
import {
  buildExplanation,
  digest,
  isPublicReview,
  observeReview,
  rankReviews,
  reviewVersion,
} from "./evidence.ts";
import { type LlmProvider, ProviderError, providersFromEnv } from "./llm.ts";

export interface AiSearchDeps {
  db: Pick<typeof firestore, "get" | "query" | "runTransaction" | "commit">;
  authenticate(req: Request): Promise<{ uid: string; displayName?: string }>;
  providers: LlmProvider[];
  now(): Date;
}

type Doc = Record<string, unknown>;
type Usage = {
  count: number;
  attempts: number;
  streak: number;
  lockedUntil: Date | null;
};
const limit = 10;
/**
 * Test only: secret AI_UNLIMITED_UIDS holds comma-separated Firebase uids that skip the daily cap and lock.
 * "*" applies to every user (temporary, for testing). Unset the secret to restore the limit.
 */
function isUnlimited(uid: string): boolean {
  const ids = (Deno.env.get("AI_UNLIMITED_UIDS") ?? "")
    .split(",").map((id) => id.trim()).filter(Boolean);
  return ids.includes("*") || ids.includes(uid);
}
/** RECOMMEND does not use the LLM or AI turns, but each call reads up to ~330 Firestore documents. */
export const recommendLimit = 20;
const minuteMs = 60_000;
const version = "court-search-rag-3"; // bump when parse rules change: invalidates aiSearchCache

function usageOf(doc: Doc | null): Usage {
  const count = Math.max(0, Number(doc?.count) || 0);
  return {
    count,
    attempts: Math.max(count, Number(doc?.attempts) || 0),
    streak: Math.max(0, Number(doc?.offTopicStreak) || 0),
    lockedUntil: doc?.lockedUntil instanceof Date ? doc.lockedUntil : null,
  };
}

function usageJson(usage: Usage, uid: string) {
  return {
    used: usage.count,
    attempts: usage.attempts,
    limit: isUnlimited(uid) ? 99_999 : limit,
    lockedUntil: usage.lockedUntil,
  };
}

function usagePath(uid: string, day: string): string {
  return `users/${uid}/aiUsage/${day}`;
}

function expiry(day: string): Date {
  return new Date(
    `${day.slice(0, 4)}-${day.slice(4, 6)}-${day.slice(6, 8)}T00:00:00+07:00`,
  );
}

async function readUsage(
  get: AiSearchDeps["db"]["get"],
  uid: string,
  day: string,
  now: Date,
) {
  const previousPath = vnTime(now).minute < 5
    ? usagePath(uid, addDays(day, -1))
    : null;
  const path = usagePath(uid, day);
  const [doc, previous] = await Promise.all([
    get(path),
    previousPath ? get(previousPath) : null,
  ]);
  const usage = usageOf(doc);
  const previousLock = usageOf(previous).lockedUntil;
  const locked =
    [usage.lockedUntil, previousLock].filter((until): until is Date =>
      until !== null && until > now
    )
      .sort((a, b) => b.getTime() - a.getTime())[0] ?? null;
  return { path, doc, usage, locked };
}

function writeUsage(
  tx: firestore.FirestoreTransaction,
  path: string,
  exists: boolean,
  value: Usage,
  day: string,
) {
  const data = {
    count: value.count,
    attempts: value.attempts,
    offTopicStreak: value.streak,
    lockedUntil: value.lockedUntil,
    expiresAt: expiry(addDays(day, 8)),
  };
  tx.write(
    exists
      ? firestore.updateWrite(path, data, ["updatedAt"])
      : firestore.createWrite(path, data, ["updatedAt"]),
  );
}

function requireAvailable(usage: Usage, locked: Date | null) {
  if (locked) throw new HttpError(409, "AI_LOCKED", "Trợ lý tạm khóa");
  if (usage.attempts >= limit || usage.count >= limit) {
    throw new HttpError(409, "QUOTA_EXCEEDED", "Đã hết lượt AI");
  }
}

async function reserve(uid: string, deps: AiSearchDeps): Promise<string> {
  const now = deps.now();
  const { day } = vnTime(now);
  await deps.db.runTransaction(async (tx) => {
    const { path, doc, usage, locked } = await readUsage(tx.get, uid, day, now);
    if (!isUnlimited(uid)) requireAvailable(usage, locked);
    usage.attempts++;
    writeUsage(tx, path, doc !== null, usage, day);
  }, 8);
  return day;
}

/** Count one RECOMMEND call in `aiUsage` (separate from AI turns) so retrieval reads stay bounded. */
async function reserveRecommend(uid: string, deps: AiSearchDeps) {
  const now = deps.now();
  const { day } = vnTime(now);
  const path = usagePath(uid, day);
  await deps.db.runTransaction(async (tx) => {
    const doc = await tx.get(path);
    const used = Math.max(0, Number(doc?.recommendCount) || 0);
    if (used >= recommendLimit) {
      throw new HttpError(
        409,
        "RECOMMEND_LIMIT",
        "Đã hết lượt đề xuất theo đánh giá hôm nay",
      );
    }
    const data = {
      recommendCount: used + 1,
      expiresAt: expiry(addDays(day, 8)),
    };
    tx.write(
      doc
        ? firestore.updateWrite(path, data, ["updatedAt"])
        : firestore.createWrite(path, data, ["updatedAt"]),
    );
  }, 8);
}

async function generate(
  uid: string,
  prompt: string,
  input: string,
  schema: object,
  deps: AiSearchDeps,
) {
  if (deps.providers.length === 0) {
    throw new HttpError(502, "AI_UNAVAILABLE", "Trợ lý chưa được cấu hình");
  }
  const started = performance.now();
  for (const [index, provider] of deps.providers.entries()) {
    const remaining = Math.floor(8_500 - (performance.now() - started));
    if (remaining < 300) {
      throw new HttpError(504, "AI_TIMEOUT", "Trợ lý phản hồi quá chậm");
    }
    const day = await reserve(uid, deps);
    try {
      const value = await provider.generate(
        prompt,
        input,
        schema,
        // Fallback gets the rest of the 8.5 s budget: Groq gpt-oss measured ~6 s, so a 3 s cap always timed out.
        index === 0 ? Math.min(6000, remaining) : remaining,
      );
      return { value, day, provider: provider.name, fallback: index > 0 };
    } catch (error) {
      if (!(error instanceof ProviderError)) throw error;
      if (error.status === 504) {
        throw new HttpError(504, "AI_TIMEOUT", "Trợ lý phản hồi quá chậm");
      }
      if (
        index + 1 < deps.providers.length &&
        (error.status === 429 || error.status >= 500)
      ) continue;
      throw new HttpError(
        502,
        "AI_UNAVAILABLE",
        "Trợ lý tạm thời không khả dụng",
      );
    }
  }
  throw new HttpError(502, "AI_UNAVAILABLE", "Trợ lý tạm thời không khả dụng");
}

async function finish(
  uid: string,
  day: string,
  deps: AiSearchDeps,
  options: { result?: Parsed; cachePath?: string; cached?: boolean } = {},
): Promise<Usage> {
  const now = deps.now();
  return await deps.db.runTransaction(async (tx) => {
    const path = usagePath(uid, day);
    const [{ doc, usage, locked }, oldCache] = await Promise.all([
      options.cached
        ? readUsage(tx.get, uid, day, now)
        : tx.get(path).then((doc) => ({
          doc,
          usage: usageOf(doc),
          locked: null,
        })),
      options.cachePath ? tx.get(options.cachePath) : null,
    ]);
    if (locked) {
      throw new HttpError(409, "AI_LOCKED", "Trợ lý tạm khóa");
    }
    if (!options.cached) usage.count++;
    if (options.result?.intent === "off_topic") {
      usage.streak++;
      if (usage.streak >= 3) {
        usage.streak = 0;
        usage.lockedUntil = new Date(now.getTime() + 5 * minuteMs);
      }
    } else if (options.result?.intent === "search" && options.result.filter) {
      usage.streak = 0;
    }
    writeUsage(tx, path, doc !== null, usage, day);
    if (options.cachePath && options.result) {
      const cache = {
        result: options.result,
        expiresAt: expiry(addDays(day, 1)),
        promptVersion: version,
      };
      tx.write(
        oldCache
          ? firestore.updateWrite(options.cachePath, cache)
          : firestore.createWrite(options.cachePath, cache),
      );
    }
    return usage;
  }, 8);
}

let catalogCache: {
  db: AiSearchDeps["db"];
  until: number;
  data: Awaited<ReturnType<typeof loadCatalog>>;
} | undefined;

async function loadCatalog(db: AiSearchDeps["db"]) {
  async function read(collection: string) {
    const docs = await db.query(collection, [{
      field: "isActive",
      op: "EQUAL",
      value: true,
    }], { limit: 100 });
    return Object.fromEntries(
      docs.filter(({ data }) => typeof data.name === "string").map((
        { id, data },
      ) => [id, data.name as string]),
    );
  }
  const [districts, amenities] = await Promise.all([
    read("districts"),
    read("amenities"),
  ]);
  return { districts, amenities };
}

async function catalog(deps: AiSearchDeps) {
  const now = deps.now().getTime();
  if (catalogCache?.db === deps.db && catalogCache.until > now) {
    return catalogCache.data;
  }
  const data = await loadCatalog(deps.db);
  catalogCache = { db: deps.db, until: now + 10 * minuteMs, data };
  return data;
}

function id(value: unknown): string {
  if (typeof value !== "string" || !/^[a-zA-Z0-9_-]{1,150}$/.test(value)) {
    throw new HttpError(400, "INVALID_ARGUMENT", "Mã không hợp lệ");
  }
  return value;
}

function sourceRef(value: unknown) {
  const data = object(value);
  if (
    typeof data.version !== "string" || !/^[a-f0-9]{64}$/.test(data.version)
  ) {
    throw new HttpError(
      400,
      "INVALID_ARGUMENT",
      "Phiên bản nguồn không hợp lệ",
    );
  }
  return { reviewId: id(data.reviewId), version: data.version };
}

async function requireActive(venueId: string, deps: AiSearchDeps) {
  if ((await deps.db.get(`venues/${venueId}`))?.status !== "ACTIVE") {
    throw new HttpError(404, "SOURCE_UNAVAILABLE", "Cơ sở không còn khả dụng");
  }
}

function sourceUnavailable(): never {
  throw new HttpError(
    404,
    "SOURCE_UNAVAILABLE",
    "Đánh giá nguồn đã thay đổi hoặc không còn hiển thị",
  );
}

async function currentSources(
  refs: ReturnType<typeof sourceRef>[],
  venueId: string,
  deps: AiSearchDeps,
) {
  return await Promise.all(refs.map(async (ref) => {
    const data = await deps.db.get(`reviews/${ref.reviewId}`);
    if (
      !isPublicReview(data, venueId) ||
      await reviewVersion(data) !== ref.version
    ) return sourceUnavailable();
    return data;
  }));
}

async function metric(deps: AiSearchDeps, data: Doc) {
  const now = deps.now();
  try {
    await deps.db.commit([
      firestore.createWrite(`aiMetrics/${crypto.randomUUID()}`, {
        ...data,
        day: vnTime(now).day,
        promptVersion: version,
        expiresAt: new Date(now.getTime() + 90 * 1440 * minuteMs),
      }, ["createdAt"]),
    ]);
  } catch {
    // Usage and source validation do not depend on analytics.
    console.warn("Unable to record ai-search metric");
  }
}

export async function handleAiSearch(
  req: Request,
  deps: AiSearchDeps = defaultDeps(),
): Promise<Response> {
  if (req.method !== "POST") {
    return toErrorResponse(
      new HttpError(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST"),
    );
  }
  const started = performance.now();
  let action = "";
  try {
    const body = object(await req.json().catch(() => null));
    if (body.warmup === true) return new Response(null, { status: 204 });
    const user = await deps.authenticate(req);
    action = typeof body.action === "string" ? body.action : "PARSE";
    const now = deps.now();
    const { day } = vnTime(now);

    switch (action) {
      case "USAGE": {
        const { usage, locked } = await readUsage(
          deps.db.get,
          user.uid,
          day,
          now,
        );
        return Response.json({
          usage: usageJson({ ...usage, lockedUntil: locked }, user.uid),
        });
      }
      case "RECOMMEND": {
        if (
          !isPreference(body.preference) || !Array.isArray(body.venueIds) ||
          body.venueIds.length > 30
        ) {
          throw new HttpError(
            400,
            "INVALID_ARGUMENT",
            "Bộ lọc đánh giá không hợp lệ",
          );
        }
        const venueIds = [...new Set(body.venueIds.map(id))];
        await reserveRecommend(user.uid, deps);
        const venues = await Promise.all(
          venueIds.map((venueId) => deps.db.get(`venues/${venueId}`)),
        );
        // Ranking reads the current reviews in this request, so the sources are already fresh.
        // Venues known to have no review are skipped to save one query each.
        const ranked = await rankReviews(
          venueIds.filter((_, i) =>
            venues[i]?.status === "ACTIVE" && venues[i]?.ratingCount !== 0
          ),
          body.preference,
          deps.db.query,
        );
        return Response.json({ recommendations: ranked });
      }
      case "SOURCE": {
        const venueId = id(body.venueId);
        await requireActive(venueId, deps);
        const [source] = await currentSources(
          [sourceRef(body.source)],
          venueId,
          deps,
        );
        return Response.json({ comment: source.comment });
      }
      case "EXPLAIN": {
        // No LLM (B1): the explanation is assembled from vetted observations, so it costs no AI turn.
        if (
          !isPreference(body.preference) || !Array.isArray(body.sources) ||
          body.sources.length < 1 || body.sources.length > 3
        ) {
          throw new HttpError(400, "INVALID_ARGUMENT", "Thiếu nguồn đánh giá");
        }
        const preference = body.preference;
        const venueId = id(body.venueId);
        await requireActive(venueId, deps);
        const refs = [
          ...new Map(
            body.sources.map(sourceRef).map((ref) => [ref.reviewId, ref]),
          ).values(),
        ];
        const reviews = await currentSources(refs, venueId, deps);
        const observations = (await Promise.all(refs.map((ref, index) =>
          observeReview(ref.reviewId, reviews[index], venueId, preference)
        ))).flat();
        const claims = buildExplanation(observations);
        if (!claims) {
          return sourceUnavailable();
        }
        await metric(deps, {
          action,
          outcome: "EXPLAINED",
          latencyMs: Math.round(performance.now() - started),
        });
        return Response.json({ claims });
      }
      case "PARSE": {
        const original = typeof body.query === "string"
          ? body.query.trim()
          : "";
        if (!original || [...original].length > 200) {
          throw new HttpError(
            400,
            "INVALID_QUERY",
            "Câu tìm phải có từ 1 đến 200 ký tự",
          );
        }
        const masked = maskQuery(
          user.displayName && user.displayName.length >= 3
            ? original.replace(
              new RegExp(
                user.displayName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"),
                "giu",
              ),
              "[name]",
            )
            : original,
        );
        const key = await digest(
          `${version}|${day}|${
            masked.normalize("NFC").toLowerCase().replace(/\s+/g, " ")
          }`,
        );
        const cachePath = `aiSearchCache/${key}`;
        const [{ locked }, cache] = await Promise.all([
          readUsage(deps.db.get, user.uid, day, now),
          deps.db.get(cachePath),
        ]);
        if (locked && !isUnlimited(user.uid)) {
          throw new HttpError(409, "AI_LOCKED", "Trợ lý tạm khóa");
        }
        if (
          cache?.promptVersion === version && cache.expiresAt instanceof Date &&
          cache.expiresAt > now && cache.result
        ) {
          const result = cache.result as Parsed;
          const usage = await finish(user.uid, day, deps, {
            cached: true,
            result,
          });
          await metric(deps, {
            action,
            outcome: "CACHED",
            intent: result.intent,
            latencyMs: Math.round(performance.now() - started),
          });
          return Response.json({
            result,
            cached: true,
            usage: usageJson(usage, user.uid),
          });
        }
        const activeCatalog = await catalog(deps);
        const generated = await generate(
          user.uid,
          parsePrompt(activeCatalog, now),
          JSON.stringify({ query: masked }),
          parseSchema,
          deps,
        );
        const result = parseResult(generated.value, masked, activeCatalog, now);
        const usage = await finish(user.uid, generated.day, deps, {
          result,
          cachePath: day === generated.day ? cachePath : undefined,
        });
        await metric(deps, {
          action,
          outcome: "PARSED",
          intent: result.intent,
          dropped: result.dropped,
          provider: generated.provider,
          fallbackUsed: generated.fallback,
          latencyMs: Math.round(performance.now() - started),
        });
        return Response.json({
          result,
          cached: false,
          usage: usageJson(usage, user.uid),
        });
      }
      default:
        throw new HttpError(400, "INVALID_ARGUMENT", "Thao tác không hợp lệ");
    }
  } catch (error) {
    if (
      (action === "PARSE" || action === "EXPLAIN") && error instanceof HttpError
    ) {
      await metric(deps, {
        action,
        outcome: error.code,
        latencyMs: Math.round(performance.now() - started),
      });
    }
    return toErrorResponse(error);
  }
}

function defaultDeps(): AiSearchDeps {
  return {
    db: firestore,
    authenticate: async (req) => {
      const user = await requireUser(req);
      return {
        uid: user.uid,
        displayName: typeof user.profile?.displayName === "string"
          ? user.profile.displayName
          : undefined,
      };
    },
    providers: providersFromEnv(),
    now: () => new Date(),
  };
}
