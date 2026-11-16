export class ProviderError extends Error {
  constructor(readonly status: number) {
    super("AI provider request failed");
  }
}

export interface LlmProvider {
  name: "gemini" | "gemini-2" | "groq";
  generate(
    prompt: string,
    input: string,
    schema: object,
    timeoutMs: number,
  ): Promise<unknown>;
}

type ProviderResponse = {
  choices?: Array<{ message?: { content?: string } }>;
  candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
};

export function providersFromEnv(): LlmProvider[] {
  // Trim: a key pasted with a trailing newline or \r makes fetch reject the header.
  const geminiKey = Deno.env.get("GEMINI_API_KEY")?.trim();
  // Optional second Gemini key, tried after the first one fails with 429/5xx and before Groq.
  const geminiKey2 = Deno.env.get("GEMINI_API_KEY_2")?.trim();
  const groqKey = Deno.env.get("GROQ_API_KEY")?.trim();
  const primary = Deno.env.get("LLM_PROVIDER") === "groq" ? "groq" : "gemini";
  const geminiModel = Deno.env.get("GEMINI_MODEL") ??
    (primary === "gemini" ? Deno.env.get("LLM_MODEL") : undefined) ??
    "gemini-3.5-flash-lite";
  const groqModel = Deno.env.get("GROQ_MODEL") ??
    (primary === "groq" ? Deno.env.get("LLM_MODEL") : undefined) ??
    "openai/gpt-oss-20b";
  const providers: LlmProvider[] = [];
  const gemini = (
    name: "gemini" | "gemini-2",
    key: string,
  ): LlmProvider => ({
    name,
    async generate(prompt, input, schema, timeoutMs) {
      const data = await request(
        `https://generativelanguage.googleapis.com/v1beta/models/${
          encodeURIComponent(geminiModel)
        }:generateContent`,
        { "x-goog-api-key": key },
        {
          systemInstruction: { parts: [{ text: prompt }] },
          contents: [{ role: "user", parts: [{ text: input }] }],
          generationConfig: {
            temperature: 0,
            maxOutputTokens: 640,
            responseMimeType: "application/json",
            responseJsonSchema: schema,
          },
        },
        timeoutMs,
      );
      return decode(
        data.candidates?.[0]?.content?.parts?.map((part) => part.text ?? "")
          .join(""),
      );
    },
  });
  if (geminiKey) providers.push(gemini("gemini", geminiKey));
  if (geminiKey2 && geminiKey2 !== geminiKey) {
    providers.push(gemini("gemini-2", geminiKey2));
  }
  if (groqKey) {
    providers.push({
      name: "groq",
      async generate(prompt, input, schema, timeoutMs) {
        const data = await request(
          "https://api.groq.com/openai/v1/chat/completions",
          { Authorization: `Bearer ${groqKey}` },
          {
            model: groqModel,
            messages: [{ role: "system", content: prompt }, {
              role: "user",
              content: input,
            }],
            temperature: 0,
            // gpt-oss is a reasoning model: hidden reasoning shares this budget, and with 768 tokens it
            // could return empty content ("json_validate_failed", failed_generation ""). Keep reasoning low.
            max_completion_tokens: 2048,
            ...(groqModel.startsWith("openai/gpt-oss")
              ? { reasoning_effort: "low" }
              : {}),
            response_format: {
              type: "json_schema",
              json_schema: { name: "ai_search", strict: true, schema },
            },
          },
          timeoutMs,
        );
        return decode(data.choices?.[0]?.message?.content);
      },
    });
  }
  // Stable sort keeps gemini before gemini-2; the primary family goes first.
  const family = (provider: LlmProvider) =>
    provider.name === "groq" ? "groq" : "gemini";
  return providers.sort((a, b) =>
    Number(family(b) === primary) - Number(family(a) === primary)
  );
}

async function request(
  url: string,
  headers: Record<string, string>,
  body: object,
  timeoutMs: number,
): Promise<ProviderResponse> {
  try {
    const response = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json", ...headers },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (!response.ok) {
      // Provider error bodies carry the reason (bad key, unknown model, rejected schema) and no secrets.
      const detail = (await response.text().catch(() => "")).slice(0, 500);
      console.warn(
        `ai-search-parse provider ${
          new URL(url).host
        } HTTP ${response.status}: ${detail}`,
      );
      throw new ProviderError(response.status);
    }
    return await response.json();
  } catch (error) {
    if (error instanceof ProviderError) throw error;
    if (error instanceof Error && error.name === "TimeoutError") {
      throw new ProviderError(504);
    }
    // Never log error.message here: fetch errors can echo request headers, including the API key.
    console.warn(
      `ai-search-parse provider ${new URL(url).host} network error: ${
        error instanceof Error ? error.name : "unknown"
      }`,
    );
    throw new ProviderError(502);
  }
}

function decode(text: string | undefined): unknown {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
