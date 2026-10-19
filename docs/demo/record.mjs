// Records docs/demo/invoice2sheet-demo.mp4: the web app driven end to end (sign-in, picking a
// spreadsheet and folder, scanning, photographing, reviewing and saving an invoice, settings),
// with captions explaining what each step does with the user's Google data.
//
//   node record.mjs            record using the existing web build
//   node record.mjs --build    build the web app with a demo OAuth client ID first
import { execFileSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { DEMO_CLIENT_ID, openStage } from './browser.mjs';
import { BUILD_DIR, INVOICE } from './fixtures.mjs';
import { ACCOUNT } from './google-mock.mjs';

const ROOT = new URL('../../', import.meta.url).pathname;
const OUT = new URL('./invoice2sheet-demo.mp4', import.meta.url).pathname;
const FRAMES = `${BUILD_DIR}frames/`;
const PHOTO = `${BUILD_DIR}invoice.jpg`;

if (process.argv.includes('--build')) {
  execFileSync('./gradlew', [':composeApp:wasmJsBrowserDistribution', `-Pgoogle.webClientId=${DEMO_CLIENT_ID}`], { cwd: ROOT, stdio: 'inherit' });
}

const CLIENT = `<div class="client"><b>OAuth client: Web application</b><br>
  Google Identity Services token client, signing in from the Invoice2Sheet web app</div>`;

const SCOPES = `<b>Scopes requested</b><br>
  <code>…/auth/spreadsheets</code>: append invoice rows, create a spreadsheet<br>
  <code>…/auth/drive</code>: list your spreadsheets and folders, upload the invoice photo<br>
  <code>email</code>, <code>profile</code>: show which account is connected`;

/** Captures the page as JPEG frames through the DevTools screencast, timed by their timestamps. */
async function startCapture(page, name) {
  const dir = `${FRAMES}${name}/`;
  mkdirSync(dir, { recursive: true });
  const cdp = await page.context().newCDPSession(page);
  const frames = [];
  cdp.on('Page.screencastFrame', ({ data, metadata, sessionId }) => {
    const file = `${dir}${String(frames.length).padStart(5, '0')}.jpg`;
    writeFileSync(file, Buffer.from(data, 'base64'));
    frames.push({ file, t: metadata.timestamp });
    cdp.send('Page.screencastFrameAck', { sessionId }).catch(() => {});
  });
  await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 92, maxWidth: 1920, maxHeight: 1080 });
  return async function stop() {
    const end = Date.now() / 1000;
    await cdp.send('Page.stopScreencast');
    // ffmpeg's concat demuxer: each frame lasts until the next one (the screencast only sends changes).
    const list = frames.map((f, i) => `file '${f.file}'\nduration ${((frames[i + 1]?.t ?? end) - f.t).toFixed(4)}`);
    writeFileSync(`${dir}list.txt`, `${list.join('\n')}\nfile '${frames.at(-1).file}'\n`);
    const clip = `${FRAMES}${name}.mp4`;
    encode(['-f', 'concat', '-safe', '0', '-i', `${dir}list.txt`], clip);
    return clip;
  };
}

function encode(input, out) {
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...input,
    '-vf', 'scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=0x10131a,fps=30,format=yuv420p',
    '-an', '-c:v', 'libx264', '-preset', 'slow', '-crf', '22', '-movflags', '+faststart', out]);
}

async function recordCard(stage, name, caption, ms) {
  const stop = await startCapture(stage.page, name);
  await stage.caption(caption);
  await stage.wait(ms);
  return stop();
}

async function main() {
  rmSync(FRAMES, { recursive: true, force: true });
  const s = await openStage();
  const clips = [];
  await s.wait(5000); // Let the Wasm app boot before the camera rolls.

  let stop = await startCapture(s.page, 'web');
  await s.caption({ step: 'Demo for Google OAuth verification', title: 'Invoice2Sheet', text:
    'Scan the QR code on a Portuguese invoice and it becomes a row in a Google Sheet you choose, with a photo of the invoice in a Drive folder you choose.', html: CLIENT });
  await s.wait(9000);

  await s.caption({ step: 'Step 1', title: 'First launch', text:
    'The app explains what it does before asking for anything. The camera is only used to read the QR code and photograph the invoice.' });
  await s.wait(4000);
  await s.tap(195, 667, { pause: 1500 });

  await s.caption({ step: 'Step 2 · Sign-in', title: 'Connect your Google Account', text:
    'Before sign-in, the app lists what it will do with your account, and links the Terms of Service and Privacy Policy.', scope: SCOPES });
  await s.wait(7000);
  await s.tap(195, 513, { pause: 200 });
  await s.consent(true);
  await s.caption({ step: 'Step 2 · Sign-in', title: 'Google asks for consent', text:
    "Tapping <i>Continue with Google</i> opens Google's own sign-in window. You pick an account and approve these scopes there. The app never sees your password.", scope: SCOPES });
  await s.wait(6600);
  await s.consent(false);
  await s.wait(2500);

  await s.caption({ step: 'Step 3 · Drive scope', title: 'Choose where invoices go', text:
    `Signed in as ${ACCOUNT.email}. The <code>drive</code> scope lists your existing spreadsheets so you can pick one. Only file names and edit dates are read.` });
  await s.wait(5500);
  await s.tap(180, 316, { pause: 1800 });
  await s.caption({ step: 'Step 3 · Drive scope', title: 'Choose a Drive folder for photos', text:
    'The same list for folders. The invoice photo is uploaded into the folder you choose, so it can sit in a folder you already use. The narrower <code>drive.file</code> scope can only reach files the app created itself.' });
  await s.wait(5000);
  await s.tap(180, 316, { pause: 1500 });
  await s.tap(195, 733, { pause: 2500 });

  await s.caption({ step: 'Step 4 · Home', title: 'Ready to scan', text:
    'Home shows this month’s invoices and the spreadsheet they go to.' });
  await s.wait(3500);
  await s.tap(195, 230, { pause: 200 });
  await s.caption({ step: 'Step 5 · Camera', title: 'Scan the invoice QR code', text:
    'Every Portuguese invoice has a fiscal QR code with the issuer’s NIF, date, ATCUD and totals. It is decoded in the browser, and the image never leaves the device.' });
  await s.wait(3200);

  await s.caption({ step: 'Step 6 · Photo', title: 'Photograph the whole invoice', text:
    'Take a photo or pick a file. The photo is kept on the device until it is uploaded to your Drive folder.' });
  await s.wait(3500);
  const chooser = s.page.waitForEvent('filechooser');
  await s.tap(195, 628, { pause: 0 });
  await (await chooser).setFiles(PHOTO);
  await s.wait(2500);
  await s.caption({ step: 'Step 6 · Photo', title: 'Straighten it', text: 'Drag the corners onto the invoice to crop it.' });
  await s.drag(43, 130, 62, 157);
  await s.drag(346, 130, 322, 155);
  await s.drag(346, 605, 330, 577);
  await s.drag(43, 605, 68, 583, { pause: 1200 });
  await s.tap(195, 684, { pause: 2500 });
  await s.tap(195, 628, { pause: 2000 });

  await s.caption({ step: 'Step 7 · Review', title: 'Check the details', text:
    'Every field from the QR code can be edited. The QR code has no shop name, so you type it once and it is remembered for that NIF.' });
  await s.wait(4000);
  await s.tap(320, 485, { pause: 400 });
  await s.type(INVOICE.merchant.slice(0, 1), { pause: 300 });
  // Typing the first letter clears the "No merchant name" warning above and re-lays out the list,
  // which drops focus on the web; tap the field again to carry on typing.
  await s.tap(340, 423, { pause: 300 });
  await s.page.keyboard.press('End');
  await s.type(INVOICE.merchant.slice(1), { pause: 1500 });
  await s.scroll(195, 400, 500, { pause: 3000 });

  await s.tap(195, 734, { pause: 300 });
  await s.caption({ step: 'Step 8 · Save', title: 'Saved in the background', text:
    'Three requests, in order: read the ATCUD column to skip duplicates (<code>spreadsheets</code>), upload the photo to the chosen folder (<code>drive</code>), then append one row with a link to the photo (<code>spreadsheets</code>).' });
  await s.wait(5000);
  const row = s.google.state.appended.at(-1);
  if (row) {
    const cells = row.headers.map((h, i) => `<tr><th>${h}</th><td>${String(row.values[i] ?? '').replace(/</g, '&lt;')}</td></tr>`).join('');
    await s.caption({ step: 'Step 8 · Save', title: `The row added to “Despesas 2026”`, text:
      `Tab “${row.tab}”, exactly as sent to the Sheets API:`, html: `<table>${cells}</table>` });
    await s.wait(8000);
  }

  await s.tap(341, 725, { pause: 300 });
  await s.caption({ step: 'Step 9 · Settings', title: 'You stay in control', text:
    'Settings shows the connected account. <i>Sign out</i> revokes the token and clears the device history. Your spreadsheets are left as they are. Access can also be removed at myaccount.google.com/permissions.' });
  await s.wait(6000);
  await s.scroll(195, 400, 600, { pause: 4500 });
  await s.scroll(195, 400, -600, { pause: 2500 });
  clips.push(await stop());

  clips.push(await recordCard(s, 'outro', { step: 'Summary', title: 'Your data stays yours', text:
    'Invoice2Sheet has no server. Invoice data goes only from your device to your own Google Sheets and Drive. The Privacy Policy and Terms of Service are linked from the sign-in screen.', html: CLIENT }, 7000));
  await s.close();

  // Re-encode every clip to the same format so the concat is clean.
  const parts = clips.map((clip, i) => {
    const part = `${FRAMES}part-${i}.mp4`;
    encode(['-i', clip], part);
    return `file '${part}'`;
  });
  writeFileSync(`${FRAMES}parts.txt`, parts.join('\n'));
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', `${FRAMES}parts.txt`, '-c', 'copy', '-movflags', '+faststart', OUT]);
  console.log(`Wrote ${OUT}`);
  console.log(s.google.state.log.join('\n'));
}

await main();
