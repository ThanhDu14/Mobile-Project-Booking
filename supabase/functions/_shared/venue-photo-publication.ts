import { HttpError } from "./auth.ts";

/** Copies only venue photos, never evidence documents, out of private onboarding staging. */
export async function publishApprovedVenuePhotos(uid: string, applicationId: string, paths: string[], documents: string[]): Promise<string[]> {
  const base=Deno.env.get("SUPABASE_URL");
  const key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const output:string[]=[];
  for(const path of paths) {
    if (documents.includes(path) || !path.startsWith(`${uid}/${applicationId}/`) || path.includes("..") || path.split("/").length !== 3) {
      throw new HttpError(400,"INVALID_VENUE_PHOTO","Ảnh cơ sở không hợp lệ hoặc trùng giấy tờ riêng tư");
    }
    if(!base || !key) throw new HttpError(503,"STORAGE_ERROR","Chưa cấu hình lưu trữ ảnh cơ sở");
    const destination=`${applicationId}/${path.split("/").pop()}`;
    const response=await fetch(`${base}/storage/v1/object/copy`,{method:"POST",headers:{Authorization:`Bearer ${key}`,apikey:key,"Content-Type":"application/json"},
      body:JSON.stringify({bucketId:"owner-docs",sourceKey:path,destinationKey:destination,destinationBucket:"venue-photos"})});
    if(!response.ok && response.status !== 409) {
      await response.body?.cancel();
      throw new HttpError(503,"STORAGE_ERROR","Không thể công khai ảnh cơ sở. Hồ sơ chưa được duyệt, hãy thử lại.");
    }
    await response.body?.cancel();
    output.push(destination);
  }
  return output;
}
