import { toErrorResponse } from "../_shared/auth.ts";
import * as defaultAuth from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";

export interface OwnerDistrictCatalogDeps {
  auth: Pick<typeof defaultAuth, "requireUser">;
  firestore: Pick<typeof defaultFirestore, "query">;
}

export async function handleOwnerDistrictCatalog(
  req: Request,
  deps: OwnerDistrictCatalogDeps = { auth: defaultAuth, firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "GET") {
    return Response.json({ error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ GET" } }, { status: 405 });
  }
  try {
    await deps.auth.requireUser(req);
    const rows = await deps.firestore.query("districts", []);
    const districts = rows.flatMap(({ id, data }) => {
      const name = data.name;
      if (typeof name !== "string" || name.trim().length === 0) return [];
      const sortOrder = typeof data.sortOrder === "number" && Number.isFinite(data.sortOrder) ? data.sortOrder : 0;
      return [{ id, name, sortOrder }];
    }).sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, "vi"));
    return Response.json({ districts });
  } catch (error) {
    return toErrorResponse(error);
  }
}
