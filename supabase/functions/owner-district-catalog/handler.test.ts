import { assertEquals } from "jsr:@std/assert@1.0.19";
import type { VerifiedToken } from "../_shared/auth.ts";
import type { OwnerDistrictCatalogDeps } from "./handler.ts";
import { handleOwnerDistrictCatalog } from "./handler.ts";

function makeDeps(rows: { id: string; data: Record<string, unknown> }[]) {
  const deps: OwnerDistrictCatalogDeps = {
    auth: { requireUser: async () => ({
      uid: "player", appRole: "PLAYER", email: null, emailVerified: false, phone: null, name: null, signInProvider: "password",
    } satisfies VerifiedToken & { appRole: "PLAYER" }) },
    firestore: { query: async (collection, filters) => {
      assertEquals(collection, "districts");
      assertEquals(filters, []);
      return rows;
    } },
  };
  return deps;
}

Deno.test("serves authenticated district catalog with deterministic sort order and excludes malformed rows", async () => {
  const deps = makeDeps([
    { id: "q3", data: { name: "Quận 3", sortOrder: 2 } },
    { id: "q1", data: { name: "Quận 1", sortOrder: 1 } },
    { id: "bad", data: { name: " " } },
  ]);
  const response = await handleOwnerDistrictCatalog(new Request("http://localhost"), deps);
  assertEquals(response.status, 200);
  assertEquals(await response.json(), {
    districts: [{ id: "q1", name: "Quận 1", sortOrder: 1 }, { id: "q3", name: "Quận 3", sortOrder: 2 }],
  });
});
