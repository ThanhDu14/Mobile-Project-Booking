// Sinh từ khóa tìm kiếm theo tiền tố (spec 090 R5; cùng ý tưởng `venues.nameKeywords` của spec 020).
// Dùng cho `groups.nameKeywords`: app truy vấn `array-contains <từ khóa đã chuẩn hóa>`.

/** Độ dài tiền tố tối đa của mỗi từ (từ dài hơn chỉ lấy tiền tố tới mức này). */
export const MAX_KEYWORD_LENGTH = 20;

/** Chữ thường, bỏ dấu tiếng Việt (`đ`/`Đ` → `d`). */
function normalize(name: string): string {
  return name
    .replace(/[đĐ]/g, "d")
    .normalize("NFD")
    .replace(/\p{Mn}/gu, "")
    .toLowerCase();
}

/**
 * Tiền tố dài 1–20 ký tự của từng từ trong `name` (tách theo ký tự không phải chữ/số), không trùng.
 * Ví dụ "CLB Cầu Lông Q7" → `c`, `cl`, `clb`, `ca`, `cau`, `l`, `lo`, `lon`, `long`, `q`, `q7`.
 */
export function nameKeywords(name: string): string[] {
  const out = new Set<string>();
  for (const word of normalize(name).split(/[^\p{L}\p{N}]+/u)) {
    for (let k = 1; k <= Math.min(word.length, MAX_KEYWORD_LENGTH); k++) out.add(word.slice(0, k));
  }
  return [...out];
}
