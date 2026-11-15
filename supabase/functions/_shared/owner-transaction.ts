import { errorResponse, HttpError, verifyIdToken } from "./auth.ts";
import type * as firestore from "./firestore.ts";

class RejectedMutation extends Error {
  constructor(readonly response: Response) { super("Owner mutation rejected"); }
}

/** Runs existing get/commit handlers against one transaction, including every authorization read. */
export async function ownerTransaction(
  request: Request,
  source: Pick<typeof firestore, "runTransaction">,
  operation: (request: Request, source: Pick<typeof firestore, "get" | "commit" | "query">) => Promise<Response>,
): Promise<Response> {
  if (request.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    await verifyIdToken(request);
    return await source.runTransaction(async tx => {
      const response = await operation(request.clone(), {
        get: path => tx.get(path),
        query: (collection, filters, options) => tx.query(collection,filters,options),
        commit: async writes => { for (const write of writes) tx.write(write); },
      });
      if (!response.ok) throw new RejectedMutation(response);
      return response;
    });
  } catch (error) {
    if (error instanceof RejectedMutation) return error.response;
    if (error instanceof HttpError) return errorResponse(error.status, error.code, error.message);
    return errorResponse(409, "CONFLICT", "Dữ liệu vừa thay đổi. Hãy tải lại trước khi thử lại.");
  }
}
