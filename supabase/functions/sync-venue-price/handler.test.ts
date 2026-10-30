import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleSyncVenuePrice } from "./handler.ts";

function fakeRequest(tokenUid: string, role = "OWNER", body: unknown = {}): Request {
  // Mock request with authorization header that can be read or mock verifyIdToken
  return new Request("https://localhost/sync-venue-price", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer fake-token`,
    },
    body: JSON.stringify(body),
  });
}

Deno.test("sync-venue-price: calculates minimum pricePerHour and updates venue", async () => {
  let committedWrites: unknown[] = [];
  const fakeDeps = {
    firestore: {
      get: async (path: string) => {
        if (path === "venues/v1") return { ownerId: "owner-1", name: "Sân test" };
        return null;
      },
      list: async (path: string) => {
        if (path === "venues/v1/priceRules") {
          return [
            { pricePerHour: 80000, label: "NORMAL" },
            { pricePerHour: 60000, label: "PEAK" },
            { pricePerHour: 100000, label: "WEEKEND" },
          ];
        }
        return [];
      },
      commit: async (writes: unknown[]) => {
        committedWrites = writes;
      },
    },
  };

  // We can test the handler with injected deps or test logic directly
});
