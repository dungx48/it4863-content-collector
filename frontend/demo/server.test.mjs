import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { once } from 'node:events';
import { createDemoServer } from './server.mjs';

async function start(server, t) {
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => new Promise(resolve => { server.close(resolve); server.closeAllConnections(); }));
  return `http://127.0.0.1:${server.address().port}`;
}

test('serves standalone demo and proxies API payload/status to a separately running backend', async t => {
  const received = [];
  const backend = await start(http.createServer(async (req, res) => {
    let body = '';
    for await (const chunk of req) body += chunk;
    received.push({ path: req.url, method: req.method, body });
    res.writeHead(422, { 'Content-Type': 'application/json', 'X-Request-Id': 'fixture' });
    res.end(JSON.stringify({ success: false, reason: 'SHORT_OR_LINK_LIST' }));
  }), t);
  const demo = await start(createDemoServer(backend), t);
  const page = await fetch(demo);
  assert.equal(page.status, 200);
  assert.match(await page.text(), /Link bài viết/);
  assert.match(await (await fetch(`${demo}/batch.mjs`)).text(), /collectBatch/);
  const payload = JSON.stringify({ url: 'https://hust.edu.vn/a' });
  const response = await fetch(`${demo}/api/v1/extract`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: payload,
  });
  assert.equal(response.status, 422);
  assert.equal(response.headers.get('x-request-id'), 'fixture');
  assert.equal((await response.json()).reason, 'SHORT_OR_LINK_LIST');
  assert.deepEqual(received, [{ path: '/api/v1/extract', method: 'POST', body: payload }]);
  assert.equal((await fetch(`${demo}/server.mjs`)).status, 404);
  assert.equal((await fetch(`${demo}/api/v1/whitelist?q=soict`)).status, 404);
  assert.equal((await fetch(`${demo}/api/v1/extract`)).status, 405);
  assert.equal((await fetch(`${demo}/api/v1/extract`, { method: 'POST', body: 'x'.repeat(8193) })).status, 413);
  assert.equal(received.length, 1);
});

test('demo stays available while backend is down', async t => {
  const unavailable = http.createServer();
  unavailable.listen(0, '127.0.0.1');
  await once(unavailable, 'listening');
  const port = unavailable.address().port;
  await new Promise(resolve => unavailable.close(resolve));
  const demo = await start(createDemoServer(`http://127.0.0.1:${port}`), t);
  assert.equal((await fetch(demo)).status, 200);
  assert.equal((await fetch(`${demo}/api/v1/index`)).status, 502);
});

test('search page and JSON import proxy support larger corpora without raising extraction limit', async t => {
  const received = [];
  const backend = await start(http.createServer(async (req, res) => {
    let size = 0;
    for await (const chunk of req) size += chunk.length;
    received.push({ method: req.method, path: req.url, size });
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end('{"ready":true,"documentCount":1}');
  }), t);
  const demo = await start(createDemoServer(backend), t);
  assert.match(await (await fetch(`${demo}/search`)).text(), /Tìm kiếm văn bản/);
  assert.match(await (await fetch(`${demo}/corpus.mjs`)).text(), /normalizeCorpus/);
  assert.equal((await (await fetch(`${demo}/sample-documents.json`)).json()).length, 6);
  const payload = JSON.stringify([{ text: 'lucene '.repeat(2000) }]);
  assert.equal((await fetch(`${demo}/api/v1/index`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: payload })).status, 200);
  assert.equal(received[0].size, Buffer.byteLength(payload));
  assert.equal((await fetch(`${demo}/api/v1/index`)).status, 200);
  assert.equal((await fetch(`${demo}/api/v1/search?q=lucene&limit=5`)).status, 200);
  assert.equal(received[2].path, '/api/v1/search?q=lucene&limit=5');
  assert.equal((await fetch(`${demo}/api/v1/index`, { method: 'DELETE' })).status, 405);
  assert.equal((await fetch(`${demo}/api/v1/index`, { method: 'POST', body: 'x'.repeat(20 * 1024 * 1024 + 1) })).status, 413);
  assert.equal(received.length, 3);
});
