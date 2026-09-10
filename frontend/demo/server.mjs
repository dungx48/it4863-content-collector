import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

const files = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/app.js', ['app.js', 'text/javascript; charset=utf-8']],
  ['/styles.css', ['styles.css', 'text/css; charset=utf-8']],
  ['/search', ['search.html', 'text/html; charset=utf-8']],
  ['/search.js', ['search.js', 'text/javascript; charset=utf-8']],
  ['/search.css', ['search.css', 'text/css; charset=utf-8']],
  ['/sample-documents.json', ['sample-documents.json', 'application/json; charset=utf-8']],
]);
const apiPaths = new Set(['/api/v1/extract', '/api/v1/whitelist', '/api/v1/index', '/api/v1/search']);

function jsonError(response, status, message) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify({ success: false, message }));
}

/** Static demo + same-origin API proxy. No collection/business logic here. */
export function createDemoServer(backendUrl = 'http://127.0.0.1:8080') {
  const backend = new URL(backendUrl);
  if (!['http:', 'https:'].includes(backend.protocol) || backend.username || backend.password
      || backend.pathname !== '/' || backend.search || backend.hash) {
    throw new Error('BACKEND_URL must be an HTTP(S) origin without credentials, path or query');
  }
  return http.createServer(async (request, response) => {
    response.setHeader('X-Content-Type-Options', 'nosniff');
    response.setHeader('Cache-Control', 'no-store');
    try {
      const url = new URL(request.url, 'http://demo.local');
      if (apiPaths.has(url.pathname)) {
        const allowed = url.pathname.endsWith('/index') ? ['GET', 'POST']
          : [url.pathname.endsWith('/extract') ? 'POST' : 'GET'];
        if (!allowed.includes(request.method)) {
          response.setHeader('Allow', allowed.join(', '));
          jsonError(response, 405, 'Method not allowed');
          return;
        }
        let size = 0;
        const maxBytes = url.pathname.endsWith('/index') ? 20 * 1024 * 1024 : 8192;
        const chunks = [];
        for await (const chunk of request) {
          size += chunk.length;
          if (size > maxBytes) {
            jsonError(response, 413, `Request exceeds ${maxBytes} bytes`);
            return;
          }
          chunks.push(chunk);
        }
        const upstream = await fetch(new URL(url.pathname + url.search, backend), {
          method: request.method,
          headers: { 'Content-Type': request.headers['content-type'] || 'application/json' },
          body: request.method === 'POST' ? Buffer.concat(chunks) : undefined,
          signal: AbortSignal.timeout(300_000),
          redirect: 'error',
        });
        const body = await upstream.arrayBuffer();
        response.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/json');
        for (const header of ['retry-after', 'x-request-id', 'allow']) {
          const value = upstream.headers.get(header);
          if (value) response.setHeader(header, value);
        }
        response.writeHead(upstream.status);
        response.end(Buffer.from(body));
        return;
      }
      if (request.method !== 'GET' && request.method !== 'HEAD') {
        response.setHeader('Allow', 'GET, HEAD');
        jsonError(response, 405, 'Method not allowed');
        return;
      }
      const asset = files.get(url.pathname);
      if (!asset) { jsonError(response, 404, 'Not found'); return; }
      const [name, type] = asset;
      const body = await readFile(new URL(`./public/${name}`, import.meta.url));
      response.writeHead(200, { 'Content-Type': type });
      response.end(request.method === 'HEAD' ? undefined : body);
    } catch {
      if (!response.headersSent) jsonError(response, 502, 'Không kết nối được backend. Kiểm tra collector API.');
      else response.end();
    }
  });
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  const port = Number(process.env.DEMO_PORT || 3000);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('DEMO_PORT must be 1..65535');
  const host = process.env.DEMO_HOST || '127.0.0.1';
  const server = createDemoServer(process.env.BACKEND_URL);
  server.listen(port, host, () => console.log(`Demo: http://${host}:${port}`));
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close());
}
