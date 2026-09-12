// File và ô dán JSON dùng chung kiểm tra; API luôn nhận mảng.
export function normalizeCorpus(text, maxBytes = 20 * 1024 * 1024) {
  if (new TextEncoder().encode(text).length > maxBytes) throw new Error('Dữ liệu vượt quá 20 MiB.');
  let data;
  try {
    data = JSON.parse(text.replace(/^\uFEFF/, ''));
  } catch {
    throw new Error('Nội dung không phải JSON hợp lệ.');
  }
  if (!Array.isArray(data)) data = [data];
  if (!data.length || data.length > 10000)
    throw new Error('Cần JSON gồm 1–10.000 văn bản.');
  if (data.some(doc => !doc || typeof doc !== 'object' || Array.isArray(doc) || typeof doc.text !== 'string' || !doc.text.trim()))
    throw new Error('Mỗi văn bản cần trường text là chuỗi không rỗng.');
  return { json: JSON.stringify(data), count: data.length };
}
