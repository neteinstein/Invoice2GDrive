// The recording stage: a local static server for the web build plus a 16:9 host page that shows
// the app in a phone-sized frame next to a caption panel explaining each step.
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';

const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript', '.wasm': 'application/wasm',
  '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.json': 'application/json', '.ttf': 'font/ttf',
};

export const PHONE = { width: 390, height: 780 };
export const VIEWPORT = { width: 1280, height: 720 };
/** The phone is drawn at this scale so a full-height app screen fits a 16:9 frame. */
export const PHONE_SCALE = 0.84;

const HOST_HTML = `<!doctype html><html><head><meta charset="utf-8"><title>Invoice2Sheet demo</title><style>
  html, body { margin: 0; height: 100%; }
  body { display: flex; align-items: center; justify-content: center; gap: 72px; overflow: hidden; background: linear-gradient(135deg, #10131a, #1d2433);
         font-family: 'Manrope', 'DejaVu Sans', system-ui, sans-serif; color: #edf0f5; }
  .holder { width: ${Math.round(PHONE.width * PHONE_SCALE)}px; height: ${Math.round(PHONE.height * PHONE_SCALE)}px; flex: none; }
  .phone { transform: scale(${PHONE_SCALE}); transform-origin: 0 0; position: relative; width: ${PHONE.width}px; height: ${PHONE.height}px; border-radius: 38px; overflow: hidden;
           box-shadow: 0 0 0 10px #05070b, 0 0 0 11px #3a4150, 0 30px 80px rgba(0,0,0,.55); background: #0C0D10; }
  iframe { width: 100%; height: 100%; border: 0; display: block; }
  .panel { width: 640px; }
  .brand { display: flex; align-items: center; gap: 14px; font-size: 22px; font-weight: 800; letter-spacing: .2px; }
  .brand img { width: 44px; height: 44px; }
  .step { margin-top: 30px; color: #7fb0ff; font-size: 15px; font-weight: 700; letter-spacing: 1.5px; text-transform: uppercase; }
  h1 { margin: 10px 0 0; font-size: 36px; line-height: 1.15; }
  p { margin: 16px 0 0; font-size: 19px; line-height: 1.5; color: #c4cad6; }
  .scope { display: none; margin-top: 26px; padding: 14px 18px; border-radius: 14px; background: rgba(127,176,255,.12);
           border: 1px solid rgba(127,176,255,.35); font-size: 16px; line-height: 1.5; color: #dce7ff; }
  .scope b { color: #fff; } .scope code { font-size: 14px; color: #9fc3ff; }
  .foot { margin-top: 30px; font-size: 13px; color: #7d8696; }
  table { margin-top: 18px; border-collapse: collapse; font-size: 13px; width: 100%; }
  td, th { padding: 5px 8px; border: 1px solid rgba(255,255,255,.14); text-align: left; }
  th { color: #9fc3ff; font-weight: 600; background: rgba(127,176,255,.08); }
  .client { margin-top: 20px; padding-left: 16px; border-left: 3px solid #7fb0ff; font-size: 17px; line-height: 1.5; color: #c4cad6; }
  .client b { color: #fff; } .client code { font-size: 15px; color: #9fc3ff; }
  .fade { animation: fade .45s ease both; } @keyframes fade { from { opacity: 0; transform: translateY(8px); } }
  #consent { position: absolute; inset: 0; z-index: 10; display: none; align-items: center; justify-content: center; background: rgba(5,7,11,.72); }
  #consent div { margin: 24px; padding: 22px; border-radius: 18px; background: #f4f6fa; color: #1b2230; font-size: 15px; line-height: 1.5; }
  #consent b { display: block; font-size: 17px; margin-bottom: 8px; }
  #consent li { margin-top: 4px; }
</style></head><body>
  <div class="holder"><div class="phone"><iframe id="app" src="app.html" allow="camera; clipboard-read; clipboard-write"></iframe>
    <div id="consent"><div><b>Google sign-in window</b>Here Google shows its own account picker and consent screen for
      <i>Invoice2Sheet</i>, asking to:<ul>
      <li>See, edit, create and delete your Google Sheets spreadsheets</li>
      <li>See, edit, create and delete your Google Drive files</li>
      <li>See your email address and personal info</li></ul></div></div></div></div>
  <div class="panel">
    <div class="brand"><img src="favicon.svg" alt="">Invoice2Sheet</div>
    <div id="caption"></div>
  </div>
<script>
  window.setCaption = function (c) {
    var el = document.getElementById('caption');
    el.innerHTML = '<div class="fade"><div class="step">' + c.step + '</div><h1>' + c.title + '</h1>' + (c.text ? '<p>' + c.text + '</p>' : '') + (c.html || '') +
      (c.scope ? '<div class="scope" style="display:block">' + c.scope + '</div>' : '') + '</div>';
  };
  window.showConsent = function (on) { document.getElementById('consent').style.display = on ? 'flex' : 'none'; };
</script></body></html>`;

/** Serves [root] (the wasmJs distribution) plus the host page at /demo.html. Resolves the base URL. */
export function startServer(root) {
  const server = createServer((req, res) => {
    const path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    if (path === '/demo.html') {
      res.writeHead(200, { 'content-type': TYPES['.html'] });
      return res.end(HOST_HTML);
    }
    const file = normalize(join(root, path === '/' ? 'index.html' : path));
    if (!file.startsWith(root) || !existsSync(file) || !statSync(file).isFile()) {
      res.writeHead(404);
      return res.end();
    }
    res.writeHead(200, { 'content-type': TYPES[extname(file)] ?? 'application/octet-stream' });
    createReadStream(file).pipe(res);
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => {
    resolve({ url: `http://127.0.0.1:${server.address().port}`, close: () => server.close() });
  }));
}
