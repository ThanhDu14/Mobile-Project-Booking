// deno-lint-ignore no-import-prefix
import { assert, assertEquals } from "jsr:@std/assert@1";
import { requireUser } from "../_shared/auth.ts";
import * as firestore from "../_shared/firestore.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import {
  maskQuery,
  parseResult,
  partOfDayFromQuery,
  pastDayInQuery,
  vnTime,
} from "./criteria.ts";
import {
  buildExplanation,
  observeReview,
  rankReviews,
  reviewObservations,
  reviewVersion,
} from "./evidence.ts";
import { type AiSearchDeps, handleAiSearch } from "./handler.ts";
import { ProviderError } from "./llm.ts";

const now = new Date("2026-11-12T03:00:00Z");
const catalog = {
  districts: { q10: "Quận 10" },
  amenities: { parking: "Gửi xe" },
};
const parseOutput = {
  intent: "search",
  type: "COURT",
  districtId: "q10",
  dayKind: "TOMORROW",
  partOfDay: "EVENING",
  startTime: "7:00",
  endTime: "9:00",
  priceMax: 100_000,
  amenityIds: ["parking"],
  preference: "GRIP",
};
const query = "Tối mai 7h–9h Q10 dưới 100k có gửi xe, sân ít trơn";

Deno.test("parse: Vietnamese date, time and preference are separate from hard filters", () => {
  const result = parseResult(parseOutput, query, catalog, now);
  assertEquals(result.intent, "search");
  assertEquals(result.filter?.date, "20261113");
  assertEquals(result.filter?.start, "1900");
  assertEquals(result.filter?.end, "2100");
  assertEquals(result.filter?.amenityIds, ["parking"]);
  assertEquals(result.preference, "GRIP");
  assertEquals(
    parseResult(parseOutput, "tối mai Q10", catalog, now).preference,
    null,
  );
});

Deno.test("parse: part of day written in the query overrides the model", () => {
  const wrong = { ...parseOutput, partOfDay: "MORNING" };
  const result = parseResult(wrong, query, catalog, now);
  assertEquals(result.filter?.partOfDay, "EVENING");
  assertEquals([result.filter?.start, result.filter?.end], ["1900", "2100"]);
  assertEquals(
    partOfDayFromQuery("sân ánh sáng tốt, giá tối đa 100k gần tôi"),
    null,
  );
  assertEquals(partOfDayFromQuery("Sáng mai sân có đèn sáng"), "MORNING");
  assertEquals(partOfDayFromQuery("chiều nay"), "AFTERNOON");
  assertEquals(partOfDayFromQuery("toi mai q10"), null);
  assertEquals(partOfDayFromQuery("sáng hoặc tối"), null);
});

Deno.test("parse: a past day in the query drops the date even when the model says today", () => {
  for (const text of ["Hôm qua 7h tối Quận 10", "hom qua 7h toi quan 10"]) {
    const result = parseResult(
      { ...parseOutput, dayKind: "TODAY" },
      text,
      catalog,
      now,
    );
    assertEquals(result.filter?.date, null);
    assertEquals(result.dropped.includes("INVALID_DATE"), true);
    assertEquals(result.intent, "unclear");
  }
  assertEquals(pastDayInQuery("Tối mai Quận 10"), false);
  assertEquals(pastDayInQuery("hom quan 10"), false);
});

Deno.test("parse: exact 30-minute request asks for another window and keeps the district", () => {
  const result = parseResult(
    { ...parseOutput, endTime: "7:30" },
    query,
    catalog,
    now,
  );
  assertEquals(result.intent, "unclear");
  assertEquals(result.filter?.start, null);
  assertEquals(result.filter?.districtId, "q10");
  assertEquals(result.dropped, ["WINDOW_TOO_SHORT"]);
  assertEquals(
    maskQuery("Q10 a.b@example.com 0912 345 678"),
    "Q10 [email] [phone]",
  );
});

Deno.test("RAG: select only applicable public evidence and explain both sides without contact data", async () => {
  const venueId = "venue-a";
  const raw = {
    venueId,
    targetType: "VENUE",
    status: "VISIBLE",
    comment: "Sàn bám tốt. Mưa thì sàn trơn. SĐT 0912345678.",
  };
  assertEquals(
    await observeReview(
      "hidden",
      { ...raw, status: "HIDDEN" },
      venueId,
      "GRIP",
    ),
    [],
  );
  assertEquals(
    await observeReview(
      "dropin",
      { ...raw, targetType: "DROP_IN" },
      venueId,
      "GRIP",
    ),
    [],
  );
  assertEquals(
    reviewObservations("Sàn không bám tốt.", "GRIP").map((item) =>
      item.supports
    ),
    [false],
  );
  const observations = await observeReview("review-a", raw, venueId, "GRIP");
  const claims = buildExplanation(observations);
  assertEquals(claims?.map(({ supports }) => supports), [true, false]);
  assert(!JSON.stringify(claims).includes("0912345678"));
  assertEquals(
    buildExplanation(observations.filter((item) => !item.supports)),
    null,
  );
});

Deno.test("RAG: owner reply or rating edit keeps the source version, comment edit changes it", async () => {
  const review = {
    venueId: "v",
    targetType: "VENUE",
    status: "VISIBLE",
    createdAt: now,
    updatedAt: now,
    comment: "Sàn bám tốt.",
  };
  const original = await reviewVersion(review);
  assertEquals(
    await reviewVersion({
      ...review,
      updatedAt: new Date(now.getTime() + 60_000),
      ownerReply: { text: "Cảm ơn" },
      overall: 3,
    }),
    original,
  );
  assert(await reviewVersion({ ...review, comment: "Sàn trơn." }) !== original);
});

Deno.test("RAG: recommendation quotes the matching clause with contacts masked", async () => {
  const ranked = await rankReviews(["v"], "GRIP", () =>
    Promise.resolve([{
      id: "r",
      path: "reviews/r",
      data: {
        venueId: "v",
        targetType: "VENUE",
        status: "VISIBLE",
        comment: "Gọi 0912345678 nhé. Sàn bám tốt, gọi 0912345678",
      },
    }]));
  assertEquals(ranked[0].quotes, [{
    reviewId: "r",
    text: "Sàn bám tốt, gọi [phone]",
    supports: true,
  }]);
});

Deno.test("RAG: retrieval only queries public venue reviews", async () => {
  const seen: unknown[] = [];
  await rankReviews(["v"], "GRIP", (_collection, filters) => {
    seen.push(...filters.map(({ field, value }) => [field, value]));
    return Promise.resolve([]);
  });
  assertEquals(seen, [["venueId", "v"], ["targetType", "VENUE"], [
    "status",
    "VISIBLE",
  ]]);
});

Deno.test("RAG: ten labelled review examples prioritize the supporting court", async () => {
  const examples = [
    ["GRIP", "Sàn có độ bám tốt.", "Mặt sàn trơn."],
    ["GRIP", "Sân ít trơn dù chơi buổi tối.", "Mặt sàn dễ trượt."],
    ["HIGH_CEILING", "Mái cao, chỗ đánh rộng.", "Trần thấp."],
    ["QUIET", "Buổi tối khá yên tĩnh.", "Quá đông."],
    ["CLEAN", "Sân rất sạch sẽ.", "Sân bẩn."],
    ["GOOD_LIGHT", "Đèn sáng, dễ nhìn.", "Thiếu sáng."],
    ["VENTILATED", "Không gian thông thoáng.", "Sân bí bách."],
    ["GOOD_SERVICE", "Nhân viên nhiệt tình.", "Phục vụ kém."],
    ["HIGH_CEILING", "Đánh cầu không vướng trần.", "Mái thấp."],
    ["QUIET", "Buổi sáng vắng người.", "Đông người."],
  ] as const;
  for (const [preference, supporting, counter] of examples) {
    const ranked = await rankReviews(
      ["wrong", "right"],
      preference,
      (_collection, filters) => {
        const venueId = filters[0].value as string;
        const data = {
          venueId,
          targetType: "VENUE",
          status: "VISIBLE",
          comment: venueId === "right" ? supporting : counter,
        };
        return Promise.resolve([{
          id: venueId,
          path: `reviews/${venueId}`,
          data,
        }]);
      },
    );
    assertEquals(ranked.map(({ venueId }) => venueId), ["right"]);
  }
});

const emulator = Boolean(
  Deno.env.get("FIRESTORE_EMULATOR_HOST") &&
    Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST"),
);

function dependencies(providers: AiSearchDeps["providers"]): AiSearchDeps {
  return {
    db: firestore,
    authenticate: requireUser,
    providers,
    now: () => now,
  };
}

async function seed(prefix: string) {
  const uid = `ai-${prefix}-${crypto.randomUUID()}`;
  await firestore.commit([
    firestore.createWrite(`users/${uid}`, { accountStatus: "ACTIVE" }),
  ]);
  const token = emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });
  return {
    uid,
    request: (body: object) => bearer(token, { body: JSON.stringify(body) }),
    usagePath: `users/${uid}/aiUsage/${vnTime(now).day}`,
  };
}

Deno.test({
  name: "Emulator: reserve every provider attempt including fallback",
  ignore: !emulator,
  async fn() {
    const { request, usagePath } = await seed("fallback");
    const calls: string[] = [];
    const deps = dependencies([
      {
        name: "gemini",
        generate: () => {
          calls.push("gemini");
          return Promise.reject(new ProviderError(429));
        },
      },
      {
        name: "groq",
        generate: () => {
          calls.push("groq");
          return Promise.resolve(parseOutput);
        },
      },
    ]);
    const response = await handleAiSearch(
      request({ query: `${query} ${crypto.randomUUID()}` }),
      deps,
    );
    assertEquals(response.status, 200);
    await response.body?.cancel();
    assertEquals(calls, ["gemini", "groq"]);
    const usage = await firestore.get(usagePath);
    assertEquals([usage?.count, usage?.attempts], [1, 2]);
  },
});

Deno.test({
  name: "Emulator: competing requests cannot spend the final attempt twice",
  ignore: !emulator,
  async fn() {
    const { request, usagePath } = await seed("race");
    await firestore.commit([
      firestore.createWrite(usagePath, { count: 7, attempts: 9 }),
    ]);
    let calls = 0;
    const deps = dependencies([{
      name: "gemini",
      generate: () => {
        calls++;
        return Promise.resolve(parseOutput);
      },
    }]);
    const suffix = crypto.randomUUID();
    const responses = await Promise.all(
      [1, 2, 3].map((i) =>
        handleAiSearch(request({ query: `${query} ${i} ${suffix}` }), deps)
      ),
    );
    assertEquals(
      responses.map(({ status }) => status).sort(),
      [200, 409, 409],
    );
    await Promise.all(responses.map((response) => response.body?.cancel()));
    assertEquals(calls, 1);
    const usage = await firestore.get(usagePath);
    assertEquals([usage?.count, usage?.attempts], [8, 10]);
  },
});

Deno.test({
  name:
    "Emulator: hidden source cannot produce an explanation and explanation spends no AI turn",
  ignore: !emulator,
  async fn() {
    const { request, usagePath } = await seed("review");
    const venueId = `venue-${crypto.randomUUID()}`;
    const reviewId = `booking-${crypto.randomUUID()}`;
    const path = `reviews/${reviewId}`;
    const review = {
      venueId,
      targetType: "VENUE",
      status: "VISIBLE",
      comment: "Sàn bám tốt. Liên hệ a.b@example.com.",
      createdAt: now,
    };
    await firestore.commit([
      firestore.createWrite(`venues/${venueId}`, {
        status: "ACTIVE",
        ratingCount: 1,
      }),
      firestore.createWrite(path, review),
    ]);
    const deps = dependencies([{
      name: "gemini",
      generate: () =>
        Promise.reject(new Error("EXPLAIN must not call the LLM")),
    }]);
    const recommendation = await handleAiSearch(
      request({ action: "RECOMMEND", venueIds: [venueId], preference: "GRIP" }),
      deps,
    );
    assertEquals(recommendation.status, 200);
    const { recommendations } = await recommendation.json();
    assertEquals(recommendations, [{
      venueId,
      sources: [{ reviewId, version: await reviewVersion(review) }],
      quotes: [{ reviewId, text: "Sàn bám tốt", supports: true }],
    }]);
    const sources = recommendations[0].sources;
    const explained = await handleAiSearch(
      request({ action: "EXPLAIN", venueId, preference: "GRIP", sources }),
      deps,
    );
    assertEquals(explained.status, 200);
    const { claims } = await explained.json();
    assertEquals(claims.length, 1);
    assert(!JSON.stringify(claims).includes("example.com"));
    // Owner reply bumps updatedAt but keeps the evidence valid (A1).
    await firestore.commit([
      firestore.updateWrite(path, { ownerReply: { text: "Cảm ơn" } }, [
        "updatedAt",
      ]),
    ]);
    const afterReply = await handleAiSearch(
      request({ action: "SOURCE", venueId, source: sources[0] }),
      deps,
    );
    assertEquals(afterReply.status, 200);
    await afterReply.body?.cancel();
    await firestore.commit([firestore.updateWrite(path, { status: "HIDDEN" })]);
    for (
      const body of [
        { action: "EXPLAIN", venueId, preference: "GRIP", sources },
        { action: "SOURCE", venueId, source: sources[0] },
      ]
    ) {
      const response = await handleAiSearch(request(body), deps);
      assertEquals(response.status, 404);
      await response.body?.cancel();
    }
    const usage = await firestore.get(usagePath);
    assertEquals(
      [usage?.count, usage?.attempts, usage?.recommendCount],
      [undefined, undefined, 1],
    );
  },
});

Deno.test({
  name: "Emulator: RECOMMEND has its own daily cap",
  ignore: !emulator,
  async fn() {
    const { request, usagePath } = await seed("recommend-cap");
    await firestore.commit([
      firestore.createWrite(usagePath, {
        count: 2,
        attempts: 2,
        recommendCount: 20,
      }),
    ]);
    const response = await handleAiSearch(
      request({ action: "RECOMMEND", venueIds: [], preference: "GRIP" }),
      dependencies([]),
    );
    assertEquals(response.status, 409);
    assertEquals((await response.json()).error.code, "RECOMMEND_LIMIT");
    const usage = await firestore.get(usagePath);
    assertEquals([usage?.count, usage?.attempts], [2, 2]);
  },
});
