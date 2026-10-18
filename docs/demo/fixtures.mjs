// Generates the demo's inputs into build/: a photo of a Portuguese invoice with its fiscal QR
// code, and a fake camera feed (Y4M) of that QR code for Chromium's fake video capture device.
import { execFileSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { chromium } from 'playwright';
import QRCode from 'qrcode';

export const BUILD_DIR = new URL('./build/', import.meta.url).pathname;

// A made-up invoice. 503100501 has a valid NIF check digit; 999999990 is AT's "final consumer".
export const INVOICE = {
  merchant: 'Padaria Central',
  nif: '503100501',
  date: '2026-10-17',
  number: 'FS 2026/004821',
  atcud: 'JFJMM9KX-4821',
  base: '15.00',
  vat: '3.45',
  total: '18.45',
};

export const QR_PAYLOAD = [
  `A:${INVOICE.nif}`, 'B:999999990', 'C:PT', 'D:FS', 'E:N', `F:${INVOICE.date.replaceAll('-', '')}`,
  `G:${INVOICE.number}`, `H:${INVOICE.atcud}`, 'I1:PT', `I7:${INVOICE.base}`, `I8:${INVOICE.vat}`,
  `N:${INVOICE.vat}`, `O:${INVOICE.total}`, 'Q:k3Q5', 'R:2741',
].join('*');

function receiptHtml(qrDataUrl) {
  const line = (label, value) => `<div class="row"><span>${label}</span><span>${value}</span></div>`;
  return `<!doctype html><html><head><style>
    body { margin: 0; background: #8a7b6a; display: flex; align-items: center; justify-content: center; height: 100vh; }
    .paper { width: 420px; padding: 34px 32px; background: #fbfaf6; color: #222; font: 15px/1.5 'DejaVu Sans Mono', monospace;
             box-shadow: 0 18px 40px rgba(0,0,0,.35); transform: rotate(-1.2deg); }
    h1 { font-size: 22px; text-align: center; margin: 0 0 4px; letter-spacing: .5px; }
    .c { text-align: center; } .row { display: flex; justify-content: space-between; }
    hr { border: 0; border-top: 1px dashed #999; margin: 12px 0; } .big { font-size: 19px; font-weight: bold; }
    img { display: block; margin: 16px auto 6px; width: 190px; height: 190px; image-rendering: pixelated; }
  </style></head><body><div class="paper">
    <h1>${INVOICE.merchant.toUpperCase()}</h1>
    <div class="c">Rua das Flores 12, 1200-195 Lisboa<br>NIF ${INVOICE.nif}</div><hr>
    <div class="c">FATURA SIMPLIFICADA<br>${INVOICE.number}<br>17/10/2026 08:42</div><hr>
    ${line('2x Pão de Deus', '3.20')}${line('1x Bolo de arroz', '1.60')}${line('3x Galão', '5.40')}
    ${line('2x Tosta mista', '7.40')}${line('1x Sumo de laranja', '0.85')}<hr>
    ${line('Base (IVA 23%)', INVOICE.base)}${line('IVA 23%', INVOICE.vat)}
    <div class="row big"><span>TOTAL EUR</span><span>${INVOICE.total}</span></div><hr>
    <div class="c">Consumidor final</div>
    <img src="${qrDataUrl}">
    <div class="c">ATCUD: ${INVOICE.atcud}</div>
    <div class="c">Obrigado pela preferência!</div>
  </div></body></html>`;
}

export async function makeFixtures() {
  mkdirSync(BUILD_DIR, { recursive: true });
  const qrDataUrl = await QRCode.toDataURL(QR_PAYLOAD, { margin: 2, width: 380, errorCorrectionLevel: 'M' });

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 640, height: 1000 }, deviceScaleFactor: 2 });
  await page.setContent(receiptHtml(qrDataUrl));
  await page.screenshot({ path: `${BUILD_DIR}invoice.jpg`, type: 'jpeg', quality: 88 });
  // The camera feed: the receipt's QR code sliding into a 640×480 frame, so the scanner is seen
  // looking before it reads the code.
  await page.setViewportSize({ width: 640, height: 480 });
  const frame = (shift) => `<body style="margin:0;background:#cfc6b8;display:flex;align-items:center;justify-content:center;height:100vh;overflow:hidden">
    <div style="background:#fbfaf6;padding:22px;transform:translate(${shift}px,${shift / 3}px) rotate(-3deg);box-shadow:0 10px 30px rgba(0,0,0,.3)">
    <img src="${qrDataUrl}" style="width:330px;height:330px;display:block"></div></body>`;
  const shifts = [...Array(24).fill(560), ...Array.from({ length: 12 }, (_, i) => 560 - 47 * (i + 1)), ...Array(24).fill(0)];
  for (const [i, shift] of shifts.entries()) {
    await page.setContent(frame(shift));
    await page.screenshot({ path: `${BUILD_DIR}camera-${String(i).padStart(3, '0')}.png` });
  }
  await browser.close();

  // Chromium's --use-file-for-fake-video-capture wants raw Y4M.
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-framerate', '15', '-i', `${BUILD_DIR}camera-%03d.png`,
    '-vf', 'scale=640:480', '-pix_fmt', 'yuv420p', `${BUILD_DIR}camera.y4m`]);
}
