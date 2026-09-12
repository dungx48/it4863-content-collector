// Chạy từng URL; một lỗi không làm mất kết quả đã thu được.
export async function collectBatch(urls, extract, { signal, onResult = () => {} } = {}) {
  const documents = [];
  const seenTexts = new Set();
  for (let index = 0; index < urls.length && !signal?.aborted; index++) {
    const url = urls[index];
    let outcome;
    try {
      const document = await extract(url, signal);
      // Chỉ loại text giống hệt trong lượt chạy này.
      if (seenTexts.has(document.text)) {
        outcome = { url, status: 'duplicate', message: 'Trùng nội dung' };
      } else {
        seenTexts.add(document.text);
        documents.push(document);
        outcome = { url, status: 'success', document };
      }
    } catch (error) {
      outcome = signal?.aborted
        ? { url, status: 'stopped', message: 'Đã dừng' }
        : { url, status: 'failed', message: error.message || 'Không thể bóc tách' };
    }
    onResult(outcome, index + 1, urls.length);
    if (outcome.status === 'stopped') break;
  }
  return { documents, stopped: Boolean(signal?.aborted) };
}
