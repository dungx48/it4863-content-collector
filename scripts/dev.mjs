import { spawn } from 'node:child_process';
import { access } from 'node:fs/promises';
import net from 'node:net';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const jar = fileURLToPath(new URL('../backend/target/collector.jar', import.meta.url));
const demo = fileURLToPath(new URL('../frontend/demo/server.mjs', import.meta.url));
const backendHost = process.env.SERVER_HOST || '127.0.0.1';
const demoHost = process.env.DEMO_HOST || '127.0.0.1';
const backendPort = Number(process.env.SERVER_PORT || 8080);
const demoPort = Number(process.env.DEMO_PORT || 3000);
const connectHost = host => ['0.0.0.0', '::'].includes(host) ? '127.0.0.1' : host;
const origin = (host, port) => `http://${host.includes(':') ? `[${host}]` : host}:${port}`;
const backendUrl = origin(connectHost(backendHost), backendPort);
const children = new Set();
const abort = new AbortController();
let stopping = false;

function stop(code = 0) {
  // Một tiến trình dừng thì launcher dọn cả backend và frontend.
  if (stopping) return;
  stopping = true;
  process.exitCode = code;
  abort.abort();
  if (process.stdin.isTTY) process.stdin.setRawMode(false);
  process.stdin.pause();
  for (const child of children) child.kill('SIGTERM');
  const deadline = setTimeout(() => {
    for (const child of children) child.kill('SIGKILL');
  }, 5000);
  deadline.unref();
}

function launch(name, command, args, env) {
  // Kế thừa terminal hiện tại để log hai tiến trình xuất cùng một chỗ.
  const child = spawn(command, args, { cwd: root, env, stdio: ['ignore', 'inherit', 'inherit'], windowsHide: true });
  children.add(child);
  child.on('error', error => {
    console.error(`${name}: ${error.message}`);
    children.delete(child);
    stop(1);
  });
  child.on('exit', code => {
    children.delete(child);
    if (!stopping) {
      console.error(`${name} stopped; shutting down both applications.`);
      stop(code || 1);
    }
  });
}

function checkPort(host, port) {
  // Bind thử trước khi khởi động để báo EADDRINUSE rõ ràng.
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Ports must be 1..65535');
  return new Promise((resolve, reject) => {
    const probe = net.createServer();
    probe.once('error', error => reject(new Error(`Cannot use ${host}:${port}: ${error.code}`)));
    probe.listen(port, host, () => probe.close(resolve));
  });
}

for (const signal of ['SIGINT', 'SIGTERM', ...(process.platform === 'win32' ? ['SIGBREAK'] : [])]) {
  process.on(signal, () => stop());
}

// Own terminal input so Ctrl+C works consistently through Windows .cmd/PTY wrappers.
if (process.stdin.isTTY) {
  process.stdin.setRawMode(true);
  process.stdin.resume();
  process.stdin.on('data', data => { if (data.includes(3)) stop(); });
}

try {
  // JAR phải được build sẵn; launcher không âm thầm build lại source Java.
  try { await access(jar); }
  catch { throw new Error('Backend JAR is missing. Run mvn verify from the repository root first.'); }
  if (backendPort === demoPort) throw new Error('SERVER_PORT and DEMO_PORT must be different');
  await Promise.all([checkPort(backendHost, backendPort), checkPort(demoHost, demoPort)]);
  if (!stopping) {
    launch('Backend', 'java', ['-jar', jar, '--serve'], {
      ...process.env, SERVER_HOST: backendHost, SERVER_PORT: String(backendPort),
    });
    const deadline = Date.now() + 30_000;
    let ready = false;
    while (!stopping && Date.now() < deadline) {
      // Chỉ mở frontend sau khi health backend thực sự UP.
      try {
        const response = await fetch(`${backendUrl}/health`, {
          signal: AbortSignal.any([abort.signal, AbortSignal.timeout(2000)]),
        });
        const body = await response.json();
        if (response.ok && body.status === 'UP') { ready = true; break; }
      } catch { /* Backend may still be starting. */ }
      if (!stopping) await delay(250);
    }
    if (!stopping) {
      if (!ready) throw new Error('Backend did not become ready within 30 seconds');
      launch('Frontend demo', process.execPath, [demo], {
        ...process.env, BACKEND_URL: backendUrl, DEMO_HOST: demoHost, DEMO_PORT: String(demoPort),
      });
      console.log(`Backend: ${backendUrl}`);
      console.log(`Frontend: ${origin(connectHost(demoHost), demoPort)}`);
      console.log('Press Ctrl+C to stop both applications.');
    }
  }
} catch (error) {
  if (!stopping) console.error(error.message);
  stop(1);
}
