import { normalizeCorpus } from './corpus.mjs';

const $ = selector => document.querySelector(selector);
let corpus = null;
let ready = false;
let busy = false;
let selectionVersion = 0;
const MAX_BYTES = 20 * 1024 * 1024;

function message(text, error = false) {
  $('#search-message').textContent = text;
  $('#search-message').className = error ? 'error' : '';
}
function controls(value) {
  // Chỉ cho tìm khi backend báo đã có index; khóa thao tác khi request đang chạy.
  busy = value;
  $('#build-index').disabled = busy || (!corpus && !$('#corpus-text').value.trim());
  $('#search-button').disabled = busy || !ready;
  $('#sample').disabled = busy;
  $('#corpus').disabled = busy;
  $('#corpus-text').disabled = busy;
  updateSettings();
  document.querySelectorAll('[data-query]').forEach(button => { button.disabled = busy; });
}
async function api(path, options = {}) {
  // Tạo index có thể lâu nên frontend dùng timeout chung năm phút.
  const response = await fetch(path, { ...options, signal: AbortSignal.timeout(300000) });
  const data = await response.json();
  if (!response.ok) throw new Error(data.message || 'Không xử lý được yêu cầu.');
  return data;
}
function renderIndex(data) {
  // Trạng thái này lấy từ index đã commit trên backend, không lấy từ file đang chọn.
  ready = data.ready;
  $('#index-state').textContent = ready
    ? 'Đã lập chỉ mục ' + data.documentCount + ' văn bản · ' + new Date(data.indexedAt).toLocaleString('vi-VN') + ' · Có thể đổi mô hình trên cùng chỉ mục'
    : 'Chưa có chỉ mục. Chọn JSON rồi bấm Tạo chỉ mục.';
  controls(busy);
}
function preview(text, error = false) {
  $('#corpus-preview').textContent = text;
  $('#corpus-preview').className = error ? 'hint error-text' : 'hint';
}
function selectCorpus(text, name, version) {
  // Bỏ kết quả đọc cũ nếu người dùng đã đổi nguồn dữ liệu.
  if (version !== selectionVersion) return;
  corpus = null;
  controls(false);
  try {
    const normalized = normalizeCorpus(text, MAX_BYTES);
    corpus = normalized.json;
    preview(name + ' · ' + normalized.count + ' văn bản · Chưa tạo chỉ mục từ dữ liệu này.');
    message('Dữ liệu đã sẵn sàng. Bấm Tạo chỉ mục để sử dụng.');
    controls(false);
    return true;
  } catch (error) {
    preview(error.message, true);
    message(error.message, true);
    controls(false);
    return false;
  }
}
$('#corpus').addEventListener('change', async event => {
  // Version ngăn lần đọc file chậm ghi đè lựa chọn mới hơn.
  const version = ++selectionVersion;
  corpus = null;
  $('#corpus-text').value = '';
  controls(false);
  const file = event.target.files[0];
  if (!file) { preview('Chưa chọn tập văn bản.'); return; }
  try {
    if (file.size > MAX_BYTES) throw new Error('File vượt quá 20 MiB.');
    controls(true);
    selectCorpus(await file.text(), file.name, version);
  } catch (error) { preview(error.message, true); message(error.message, true); controls(false); }
});
$('#corpus-text').addEventListener('input', () => {
  // Chỉ kiểm tra JSON khi bấm tạo chỉ mục, tránh parse mỗi lần gõ.
  ++selectionVersion;
  corpus = null;
  $('#corpus').value = '';
  preview($('#corpus-text').value.trim()
    ? 'Đã nhập JSON. Nội dung sẽ được kiểm tra khi tạo chỉ mục.'
    : 'Chưa chọn tập văn bản.');
  message('');
  controls(false);
});
$('#sample').addEventListener('click', async () => {
  const version = ++selectionVersion;
  corpus = null;
  controls(true);
  try {
    const response = await fetch('/sample-documents.json');
    if (!response.ok) throw new Error('Không tải được dữ liệu mẫu.');
    $('#corpus').value = '';
    $('#corpus-text').value = '';
    selectCorpus(await response.text(), 'Bộ mẫu minh họa TF-IDF (tự soạn)', version);
  } catch (error) { message(error.message, true); controls(false); }
});
$('#build-index').addEventListener('click', async () => {
  // Nội dung dán được parse tại đây để không parse lại sau mỗi phím gõ.
  if (busy) return;
  const pasted = $('#corpus-text').value.trim();
  if (pasted && !selectCorpus(pasted, 'Nội dung JSON đã dán', selectionVersion)) return;
  if (!corpus) return;
  controls(true);
  message('Đang tạo chỉ mục…');
  $('#search-results').hidden = true;
  try {
    renderIndex(await api('/api/v1/index', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: corpus }));
    preview($('#corpus-preview').textContent.replace('Chưa tạo chỉ mục từ dữ liệu này.', 'Đã tạo chỉ mục từ tập văn bản này.'));
    message('Tạo chỉ mục thành công. Nhập truy vấn để tìm kiếm.');
    $('#query').focus();
  } catch (error) { message(error.message, true); }
  finally { controls(false); }
});
function element(tag, text, className) {
  // Dùng textContent để dữ liệu corpus không được thực thi như HTML.
  const node = document.createElement(tag);
  node.textContent = text;
  if (className) node.className = className;
  return node;
}
function detail(label, text, tag = 'p') {
  const node = document.createElement('details');
  node.append(element('summary', label), element(tag, text, 'full-text'));
  return node;
}
function renderResults(data) {
  // Backend đã xếp hạng; frontend chỉ dựng card theo đúng thứ tự trả về.
  $('#search-results').hidden = false;
  $('#result-summary').textContent = data.total
    ? 'Hiển thị ' + data.results.length + '/' + data.total + ' kết quả · ' + data.similarity + ' · ' + data.options.operator.toUpperCase() + ' · Điểm giảm dần'
    : 'Không có văn bản khớp truy vấn. Thử từ khác hoặc kiểm tra tập văn bản đã nạp.';
  $('#query-tokens').textContent = 'Token truy vấn: ' + data.tokens.join(' · ');
  $('#hits').replaceChildren(...data.results.map((hit, index) => {
    const card = element('article', '', 'hit');
    card.append(element('div', '#' + (index + 1) + ' · Điểm ' + hit.score.toFixed(6) + ' · ' + hit.id, 'hit-score'));
    card.append(element('h3', hit.title || '(Không có tiêu đề)'));
    if (hit.url) {
      try {
        const url = new URL(hit.url);
        if (['http:', 'https:'].includes(url.protocol) && !url.username && !url.password) {
          const link = element('a', hit.url);
          link.href = url.href; link.target = '_blank'; link.rel = 'noopener noreferrer';
          card.append(link);
        }
      } catch { /* A source URL is optional and never executed as markup. */ }
    }
    card.append(element('p', hit.text.slice(0, 320) + (hit.text.length > 320 ? '…' : '')));
    card.append(detail('Xem toàn bộ văn bản', hit.text), detail('Giải thích điểm · ' + data.similarity, hit.explanation, 'pre'));
    return card;
  }));
}
$('#search-form').addEventListener('submit', async event => {
  // URLSearchParams encode truy vấn trước khi gắn vào query string.
  event.preventDefault();
  if (busy || !ready) return;
  controls(true);
  $('#search-results').hidden = true;
  message('Đang tìm kiếm…');
  try {
    const params = new URLSearchParams({ q: $('#query').value, limit: $('#limit').value,
      model: $('#model').value, operator: $('#operator').value });
    if ($('#model').value === 'bm25') { params.set('k1', $('#k1').value); params.set('b', $('#b').value); }
    if ($('#model').value === 'lm') params.set('lambda', $('#lambda').value);
    renderResults(await api('/api/v1/search?' + params));
    message('Tìm kiếm hoàn tất.');
  } catch (error) { message(error.message, true); }
  finally { controls(false); }
});
document.querySelectorAll('[data-query]').forEach(button => button.addEventListener('click', () => {
  $('#query').value = button.dataset.query;
  if (ready) $('#search-form').requestSubmit();
  else message('Hãy tạo chỉ mục trước khi tìm kiếm.');
}));
api('/api/v1/index').then(renderIndex).catch(error => {
  $('#index-state').textContent = 'Không kiểm tra được chỉ mục. Kiểm tra backend rồi tải lại trang.';
  message(error.message, true);
});

function updateSettings() {
  const model = $('#model').value;
  $('#bm25-params').hidden = model !== 'bm25';
  $('#lm-params').hidden = model !== 'lm';
  for (const id of ['model', 'operator', 'query', 'limit']) $('#' + id).disabled = busy;
  for (const id of ['k1', 'b']) { $('#' + id).disabled = busy || model !== 'bm25'; $('#' + id).required = model === 'bm25'; }
  $('#lambda').disabled = busy || model !== 'lm';
  $('#lambda').required = model === 'lm';
  $('#model-help').textContent = {
    classic: 'TF-IDF Lucene: TF = sqrt(freq), IDF có làm mịn, chuẩn hóa theo độ dài trường. Giữ làm mốc so sánh.',
    cosine: 'VSM lnc.ltc: log TF, IDF cho truy vấn và chuẩn hóa cosine. Điểm dùng tần suất từ lặp trong truy vấn.',
    bm25: 'BM25: điều chỉnh k1 và b để quan sát ảnh hưởng của tần suất từ và độ dài văn bản.',
    lm: 'LM: λ là trọng số mô hình văn bản theo slide; Lucene nhận 1 − λ. Điểm log không phải xác suất hoặc phần trăm.'
  }[model];
}
$('#model').addEventListener('change', updateSettings);
updateSettings();
