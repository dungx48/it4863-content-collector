// Phần tử giao diện và trạng thái dùng chung.
const form = document.querySelector('#extract-form');
const urlInput = document.querySelector('#url');
const submit = document.querySelector('#submit');
const statusBox = document.querySelector('#status');
const result = document.querySelector('#result');
const linksBody = document.querySelector('#links');
const noLinks = document.querySelector('#no-links');
const whitelistOptions = document.querySelector('#whitelist-suggestions');
const whitelistStatus = document.querySelector('#whitelist-status');
const fileInput = document.querySelector('#txt-import');
const importPanel = document.querySelector('#import-panel');
const importFilter = document.querySelector('#import-filter');
const importedSelect = document.querySelector('#imported-urls');
const importNote = document.querySelector('#import-note');
let latestResult = null;
document.querySelector('#download-json').addEventListener('click', () => {
  if (!latestResult) return;
  const blob = new Blob([JSON.stringify([latestResult], null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'documents.json';
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});
let importedUrls = [];
let suggestionTimer = null;
let suggestionRequest = null;

const sampleUrl = 'https://soict.hust.edu.vn/bai-giang-dai-chung-cua-unesco-dao-duc-trong-tri-tue-nhan-tao.html';
const numberFormat = new Intl.NumberFormat('vi-VN');
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_IMPORTED_URLS = 50000;
const MAX_VISIBLE_IMPORTED_URLS = 200;

// Nguồn URL: link mẫu, whitelist đóng gói và file TXT cục bộ.
document.querySelector('#example').addEventListener('click', () => {
  urlInput.value = sampleUrl;
  urlInput.focus();
});

urlInput.addEventListener('focus', () => {
  if (!whitelistOptions.children.length) loadWhitelistSuggestions(urlInput.value.trim());
});

urlInput.addEventListener('input', () => {
  clearTimeout(suggestionTimer);
  suggestionTimer = setTimeout(() => loadWhitelistSuggestions(urlInput.value.trim()), 180);
});

fileInput.addEventListener('change', async () => {
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

importedSelect.addEventListener('change', () => {
  if (!importedSelect.value) return;
  urlInput.value = importedSelect.value;
  urlInput.focus();
});

document.querySelector('#clear-import').addEventListener('click', () => {
  importedUrls = [];
  importedSelect.replaceChildren();
  fileInput.value = '';
  importPanel.hidden = true;
});

// Gửi một URL tới API bóc tách.
form.addEventListener('submit', async (event) => {
  event.preventDefault();
  setLoading(true);
  showStatus('Đang tải và phân tích nội dung bài viết…', 'busy');
  result.hidden = true;
  try {
    const response = await fetch('/api/v1/extract', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({url: urlInput.value.trim()})
    });
    const data = await response.json();
    if (!response.ok || !data.success) throw new Error(data.message || data.reason || 'Không thể bóc tách nội dung.');
    latestResult = data;
    render(data);
    statusBox.hidden = true;
    result.hidden = false;
    result.scrollIntoView({behavior: 'smooth', block: 'start'});
  } catch (error) {
    latestResult = null;
    showStatus(error.message || 'Không thể kết nối với chương trình.', 'error');
  } finally {
    setLoading(false);
  }
});

document.querySelector('#copy-text').addEventListener('click', async (event) => {
  if (latestResult) await copy(latestResult.text, event.currentTarget);
});

document.querySelector('#copy-json').addEventListener('click', async (event) => {
  if (latestResult) await copy(JSON.stringify(latestResult, null, 2), event.currentTarget);
});

if (new URLSearchParams(window.location.search).get('demo') === '1') {
  urlInput.value = sampleUrl;
  form.requestSubmit();
}

function render(data) {
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
  document.querySelector('table').hidden = data.links.length === 0;
}

// Chỉ nạp tối đa 20 gợi ý phù hợp để datalist luôn nhẹ.
async function loadWhitelistSuggestions(query) {
  suggestionRequest?.abort();
  suggestionRequest = new AbortController();
  try {
    const response = await fetch('/api/v1/whitelist?q=' + encodeURIComponent(query), {signal: suggestionRequest.signal});
    const data = await response.json();
    if (!response.ok || !data.success) throw new Error();
    whitelistOptions.replaceChildren(...data.urls.map(url => {
      const option = document.createElement('option');
      option.value = url;
      return option;
    }));
    whitelistStatus.textContent = 'Whitelist N06_Ctrl+F: ' + numberFormat.format(data.total) + ' URL';
  } catch (error) {
    if (error.name !== 'AbortError') whitelistStatus.textContent = 'Không tải được gợi ý whitelist';
  }
}

// Trích URL trong văn bản, chỉ giữ domain HUST và loại trùng.
function parseImportedUrls(text) {
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

// Giới hạn số option được vẽ; toàn bộ URL vẫn được giữ để tìm kiếm.
function renderImportedUrls() {
  const query = importFilter.value.trim().toLowerCase();
  const matches = importedUrls.filter(url => !query || url.toLowerCase().includes(query));
  const visible = matches.slice(0, MAX_VISIBLE_IMPORTED_URLS);
  importedSelect.replaceChildren(...visible.map(url => {
    const option = document.createElement('option');
    option.value = url;
    option.textContent = url;
    return option;
  }));
  importNote.className = 'import-note';
  importNote.textContent = matches.length > visible.length
    ? `Hiển thị ${numberFormat.format(visible.length)}/${numberFormat.format(matches.length)} kết quả; gõ thêm để lọc.`
    : `${numberFormat.format(matches.length)} kết quả. Chọn một URL để điền vào ô phía trên.`;
}

function showImportError(message) {
  importedUrls = [];
  fileInput.value = '';
  importPanel.hidden = false;
  document.querySelector('#import-title').textContent = 'Không thể import';
  document.querySelector('#import-count').textContent = '';
  importedSelect.replaceChildren();
  importNote.className = 'import-note error-text';
  importNote.textContent = message;
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
  submit.disabled = loading;
  submit.classList.toggle('loading', loading);
  urlInput.disabled = loading;
}

async function copy(value, button) {
  const old = button.textContent;
  try {
    await navigator.clipboard.writeText(value);
    button.textContent = 'Đã sao chép';
  } catch {
    button.textContent = 'Không thể sao chép';
  }
  setTimeout(() => button.textContent = old, 1500);
}
