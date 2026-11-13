import { HttpError } from "./auth.ts";
import { createWrite, type runTransaction } from "./firestore.ts";

/** Both voucher writers use the same transaction read set; no registry collection is added. */
export async function createUniqueVoucher(
  transaction: typeof runTransaction, path: string, code: string, data: Record<string, unknown>,
): Promise<void> {
  await transaction(async tx => {
    const system = await tx.get(`systemVouchers/${code}`);
    const venues = await tx.query("vouchers", [{field:"code",op:"EQUAL",value:code}], {allDescendants:true,limit:1});
    if (system || venues.length) throw new HttpError(409,"VOUCHER_EXISTS","Mã voucher đã tồn tại trên hệ thống");
    tx.write(createWrite(path,data));
  });
}
