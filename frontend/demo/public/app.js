import { collectBatch } from './batch.mjs';

// Phần tử giao diện và trạng thái dùng chung.
const form = document.querySelector('#extract-form');
const urlInput = document.querySelector('#url');
const submit = document.querySelector('#submit');
const statusBox = document.querySelector('#status');
const result = document.querySelector('#result');
const linksBody = document.querySelector('#links');
const noLinks = document.querySelector('#no-links');
const fileInput = document.querySelector('#txt-import');
const importPanel = document.querySelector('#import-panel');
const importFilter = document.querySelector('#import-filter');
const importedList = document.querySelector('#imported-urls');
const importNote = document.querySelector('#import-note');
const selectedCount = document.querySelector('#selected-count');
const batchSubmit = document.querySelector('#batch-submit');
const batchStop = document.querySelector('#batch-stop');
const batchResults = document.querySelector('#batch-results');
const batchProgress = document.querySelector('#batch-progress');
const batchRows = document.querySelector('#batch-rows');
const exportResults = document.querySelector('#export-results');
let latestResults = []; // Các bài không trùng để xuất cả lô.
let activeResult = null; // Bài đang mở trong hộp thoại.
let batchController = null;
let batchRunning = false;
let singleRunning = false;
const selectedUrls = new Set();
document.querySelector('#download-json').addEventListener('click', () => {
  if (latestResults.length) downloadJson(latestResults, 'documents.json');
});
document.querySelector('#download-detail-json').addEventListener('click', () => {
  if (activeResult) downloadJson([activeResult], 'document.json');
});
document.querySelector('#copy-detail-json').addEventListener('click', async (event) => {
  if (activeResult) await copy(JSON.stringify([activeResult], null, 2), event.currentTarget);
});

function downloadJson(documents, filename) {
  // Luôn xuất mảng để nạp trực tiếp vào TF-IDF, kể cả một bài.
  const blob = new Blob([JSON.stringify(documents, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
let importedUrls = [];

const sampleUrl = 'https://soict.hust.edu.vn/bai-giang-dai-chung-cua-unesco-dao-duc-trong-tri-tue-nhan-tao.html';
const numberFormat = new Intl.NumberFormat('vi-VN');
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_IMPORTED_URLS = 50000;
const MAX_VISIBLE_IMPORTED_URLS = 200;
const MAX_SELECTED_URLS = 10;

// Điền link mẫu; người dùng vẫn chủ động bấm Bóc tách.
document.querySelector('#example').addEventListener('click', () => {
  urlInput.value = sampleUrl;
  urlInput.focus();
});

fileInput.addEventListener('change', async () => {
  // File chỉ được đọc trong trình duyệt, không upload lên backend.
  const file = fileInput.files[0];
  if (!file) return;
  if (file.size > MAX_FILE_BYTES) {
    showImportError('File vượt quá giới hạn 10 MiB.');
    return;
  }
  try {
    importedUrls = parseImportedUrls(await file.text()).slice(0, MAX_IMPORTED_URLS);
    if (!importedUrls.length) {
      showImportError('Không tìm thấy URL HUST hợp lệ trong file.');
      return;
    }
    document.querySelector('#import-title').textContent = file.name;
    document.querySelector('#import-count').textContent = numberFormat.format(importedUrls.length) + ' URL HUST';
    selectedUrls.clear();
    importFilter.value = '';
    importPanel.hidden = false;
    renderImportedUrls();
  } catch {
    showImportError('Không đọc được file TXT này.');
  }
});

importFilter.addEventListener('input', renderImportedUrls);
importFilter.addEventListener('keydown', event => {
  if (event.key === 'Enter') event.preventDefault();
});

importedList.addEventListener('change', event => {
  // Set giữ lựa chọn khi danh sách được vẽ lại do lọc.
  const checkbox = event.target;
  if (checkbox.type !== 'checkbox') return;
  if (checkbox.checked && selectedUrls.size < MAX_SELECTED_URLS) selectedUrls.add(checkbox.value);
  else {
    checkbox.checked = false;
    selectedUrls.delete(checkbox.value);
  }
  if (checkbox.checked) urlInput.value = checkbox.value;
  updateSelection();
});

document.querySelector('#clear-import').addEventListener('click', () => {
  importedUrls = [];
  selectedUrls.clear();
  importedList.replaceChildren();
  fileInput.value = '';
  importPanel.hidden = true;
  updateSelection();
});

document.querySelector('#clear-selection').addEventListener('click', () => {
  selectedUrls.clear();
  renderImportedUrls();
});

batchSubmit.addEventListener('click', async () => {
  // Giữ thứ tự trong file TXT, không phụ thuộc thứ tự bấm chọn.
  const urls = importedUrls.filter(url => selectedUrls.has(url));
  if (!urls.length || batchRunning) return;
  latestResults = [];
  activeResult = null;
  result.close();
  exportResults.hidden = true;
  batchRows.replaceChildren();
  batchResults.hidden = false;
  batchProgress.textContent = `0/${urls.length} URL`;
  batchController = new AbortController();
  const counts = { success: 0, duplicate: 0, failed: 0, stopped: 0 };
  setBatchLoading(true);
  showStatus('Đang bóc tách danh sách URL…', 'busy');
  try {
    const batch = await collectBatch(urls, extractOne, {
      signal: batchController.signal,
      onResult: (outcome, processed, total) => {
        // Cập nhật từng dòng ngay khi URL hoàn tất thay vì chờ cả lô.
        counts[outcome.status]++;
        appendBatchOutcome(outcome);
        batchProgress.textContent = `${processed}/${total} URL · ${counts.success} thành công · ${counts.failed} lỗi · ${counts.duplicate} trùng`;
      }
    });
    latestResults = batch.documents;
    exportResults.hidden = !latestResults.length;
    setText('#export-count', `${latestResults.length} văn bản sẵn sàng cho TF-IDF`);
    const state = batch.stopped ? 'Đã dừng' : 'Đã hoàn tất';
    showStatus(`${state}: ${latestResults.length}/${urls.length} văn bản sẵn sàng để tải hoặc sao chép.`, latestResults.length ? 'busy' : 'error');
  } finally {
    batchController = null;
    setBatchLoading(false);
  }
});

batchStop.addEventListener('click', () => {
  batchStop.disabled = true;
  batchStop.textContent = 'Đang dừng…';
  batchController?.abort();
});

async function extractOne(url, signal) {
  // Một hàm gọi API dùng chung cho chạy đơn và chạy lô.
  const response = await fetch('/api/v1/extract', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({url}),
    signal
  });
  const data = await response.json();
  if (!response.ok || !data.success) throw new Error(data.message || data.reason || 'Không thể bóc tách nội dung.');
  return data;
}

// Gửi một URL tới API bóc tách.
form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (batchRunning) return;
  setLoading(true);
  showStatus('Đang tải và phân tích nội dung bài viết…', 'busy');
  latestResults = [];
  activeResult = null;
  result.close();
  exportResults.hidden = true;
  batchResults.hidden = true;
  batchRows.replaceChildren();
  try {
    const data = await extractOne(urlInput.value.trim());
    latestResults = [data];
    // Kết quả đơn dùng cùng bảng và hộp thoại với kết quả lô.
    appendBatchOutcome({ status: 'success', document: data, url: data.url });
    batchResults.hidden = false;
    batchProgress.textContent = '1/1 URL · 1 thành công';
    exportResults.hidden = false;
    setText('#export-count', '1 văn bản sẵn sàng cho TF-IDF');
    statusBox.hidden = true;
    batchResults.scrollIntoView({behavior: 'smooth', block: 'start'});
  } catch (error) {
    latestResults = [];
    activeResult = null;
    showStatus(error.message || 'Không thể kết nối với chương trình.', 'error');
  } finally {
    setLoading(false);
  }
});

document.querySelector('#copy-text').addEventListener('click', async (event) => {
  if (activeResult) await copy(activeResult.text, event.currentTarget);
});

document.querySelector('#copy-json').addEventListener('click', async (event) => {
  if (latestResults.length) await copy(JSON.stringify(latestResults, null, 2), event.currentTarget);
});

if (new URLSearchParams(window.location.search).get('demo') === '1') {
  urlInput.value = sampleUrl;
  form.requestSubmit();
}

function render(data) {
  // Đổ bài đang chọn vào hộp thoại; không sửa tập JSON xuất chung.
  setText('#http-status', data.httpStatus);
  setText('#content-type', data.contentType);
  setText('#char-count', numberFormat.format(data.text.length));
  setText('#link-count', data.links.length);
  setText('#elapsed', data.elapsedMs + ' ms');
  setText('#article-title', data.title || '(Không có tiêu đề)');
  setText('#article-text', data.text);

  const finalUrl = document.querySelector('#final-url');
  finalUrl.textContent = data.url;
  finalUrl.href = data.url;

  linksBody.replaceChildren();
  for (const link of data.links) {
    const row = document.createElement('tr');
    const description = document.createElement('td');
    description.textContent = link.text || '(Không có mô tả)';
    const urlCell = document.createElement('td');
    const anchor = document.createElement('a');
    anchor.href = link.url;
    anchor.textContent = link.url;
    anchor.target = '_blank';
    anchor.rel = 'noopener noreferrer';
    urlCell.append(anchor);
    row.append(description, urlCell);
    linksBody.append(row);
  }
  noLinks.hidden = data.links.length !== 0;
  document.querySelector('#links-table').hidden = data.links.length === 0;
}

// Trích URL trong văn bản, chỉ giữ domain HUST và loại trùng.
function parseImportedUrls(text) {
  // Regex tìm ứng viên; URL API kiểm tra cú pháp/domain lần cuối.
  const found = text.match(/https?:\/\/[^\s<>"']+/gi) || [];
  const unique = new Set();
  for (const candidate of found) {
    const value = candidate.replace(/[,;]+$/, '');
    try {
      const parsed = new URL(value);
      const host = parsed.hostname.toLowerCase();
      if ((host === 'hust.edu.vn' || host.endsWith('.hust.edu.vn')) &&
          (parsed.protocol === 'http:' || parsed.protocol === 'https:')) unique.add(parsed.href);
    } catch { /* Ignore malformed text fragments. */ }
  }
  return [...unique];
}

// Chỉ vẽ tối đa 200 URL; lựa chọn vẫn được giữ khi lọc.
function renderImportedUrls() {
  const query = importFilter.value.trim().toLowerCase();
  const matches = importedUrls.filter(url => !query || url.toLowerCase().includes(query));
  const visible = matches.slice(0, MAX_VISIBLE_IMPORTED_URLS);
  importedList.replaceChildren(...visible.map(url => {
    const label = document.createElement('label');
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.value = url;
    checkbox.checked = selectedUrls.has(url);
    const text = document.createElement('span');
    text.textContent = url;
    label.append(checkbox, text);
    return label;
  }));
  importNote.className = 'import-note';
  importNote.textContent = matches.length > visible.length
    ? `Hiển thị ${numberFormat.format(visible.length)}/${numberFormat.format(matches.length)} kết quả; gõ thêm để lọc.`
    : `${numberFormat.format(matches.length)} kết quả. Chọn tối đa ${MAX_SELECTED_URLS} URL để bóc tách theo lô.`;
  updateSelection();
}

function updateSelection() {
  // Khóa input trong lúc chạy và khóa ô chưa chọn khi đã đủ 10 URL.
  const running = batchRunning || singleRunning;
  selectedCount.textContent = `Đã chọn ${selectedUrls.size}/${MAX_SELECTED_URLS}`;
  document.querySelector('#clear-selection').disabled = running || !selectedUrls.size;
  batchSubmit.disabled = running || !selectedUrls.size;
  const full = selectedUrls.size >= MAX_SELECTED_URLS;
  importedList.querySelectorAll('input').forEach(checkbox => {
    checkbox.disabled = running || (full && !checkbox.checked);
  });
}

function appendBatchOutcome(outcome) {
  // Chỉ dựng chi tiết khi người dùng chọn bài; bảng giữ gọn.
  const labels = { success: 'Thành công', duplicate: 'Trùng nội dung', failed: 'Lỗi', stopped: 'Đã dừng' };
  const row = document.createElement('tr');
  const state = document.createElement('td');
  state.textContent = labels[outcome.status];
  state.className = outcome.status;
  const detail = document.createElement('td');
  const action = document.createElement('td');
  if (outcome.document) {
    detail.textContent = outcome.document.title || '(Không có tiêu đề)';
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = 'Xem chi tiết';
    button.setAttribute('aria-label', `Xem chi tiết: ${detail.textContent}`);
    button.addEventListener('click', () => {
      activeResult = outcome.document;
      render(activeResult);
      result.querySelector('.detail-links').open = false;
      result.showModal();
      result.scrollTop = 0;
    });
    action.append(button);
  } else detail.textContent = outcome.message;
  const source = document.createElement('td');
  const link = document.createElement('a');
  link.href = outcome.document?.url || outcome.url;
  link.textContent = new URL(link.href).hostname;
  link.title = link.href;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  source.append(link);
  row.append(state, detail, source, action);
  batchRows.append(row);
}

function showImportError(message) {
  // Xóa dữ liệu file cũ để người dùng không vô tình chạy nhầm danh sách.
  importedUrls = [];
  selectedUrls.clear();
  fileInput.value = '';
  importPanel.hidden = false;
  document.querySelector('#import-title').textContent = 'Không thể import';
  document.querySelector('#import-count').textContent = '';
  importedList.replaceChildren();
  importNote.className = 'import-note error-text';
  importNote.textContent = message;
  updateSelection();
}

function setText(selector, value) {
  document.querySelector(selector).textContent = String(value ?? '—');
}

function showStatus(message, type) {
  statusBox.textContent = message;
  statusBox.className = 'status ' + type;
  statusBox.hidden = false;
}

function setLoading(loading) {
  singleRunning = loading;
  submit.disabled = loading;
  submit.classList.toggle('loading', loading);
  urlInput.disabled = loading;
  fileInput.disabled = loading;
  importFilter.disabled = loading;
  document.querySelector('#example').disabled = loading;
  document.querySelector('#clear-import').disabled = loading;
  updateSelection();
}

function setBatchLoading(loading) {
  batchRunning = loading;
  batchStop.hidden = !loading;
  batchStop.disabled = false;
  batchStop.textContent = 'Dừng';
  submit.disabled = loading;
  urlInput.disabled = loading;
  fileInput.disabled = loading;
  importFilter.disabled = loading;
  document.querySelector('#example').disabled = loading;
  document.querySelector('#clear-import').disabled = loading;
  updateSelection();
}

async function copy(value, button) {
  // Phản hồi ngay trên nút rồi trả lại nhãn ban đầu.
  const old = button.textContent;
  try {
    await navigator.clipboard.writeText(value);
    button.textContent = 'Đã sao chép';
  } catch {
    button.textContent = 'Không thể sao chép';
  }
  setTimeout(() => button.textContent = old, 1500);
}
