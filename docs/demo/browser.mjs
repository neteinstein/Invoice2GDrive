// Launches Chromium on the stage with Google mocked, a fake camera, and the app's OAuth client ID
// pre-filled, so the web build runs end to end without a Google account.
import { existsSync } from 'node:fs';
import { chromium } from 'playwright';
import { BUILD_DIR, makeFixtures } from './fixtures.mjs';
import { createGoogleMock } from './google-mock.mjs';
import { PHONE, startServer, VIEWPORT } from './stage.mjs';

export const DIST = new URL('../../composeApp/build/dist/wasmJs/', import.meta.url).pathname;
// Build with this client ID baked in, as the deployed site has its own (`record.mjs --build` does it).
export const DEMO_CLIENT_ID = '123456789012-invoice2sheetdemo.apps.googleusercontent.com';

function distDir() {
  if (existsSync(`${DIST}productionExecutable/app.html`)) return `${DIST}productionExecutable`;
  throw new Error('No web build found. Run `node record.mjs --build`, or build it with the demo client ID yourself.');
}

export async function openStage({ scale = 1.5 } = {}) {
  await makeFixtures();
  const server = await startServer(distDir());
  const browser = await chromium.launch({
    args: [
      '--use-fake-ui-for-media-stream',
      '--use-fake-device-for-media-stream',
      `--use-file-for-fake-video-capture=${BUILD_DIR}camera.y4m`,
    ],
  });
  const context = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: scale, locale: 'en-GB', timezoneId: 'Europe/Lisbon' });
  await context.grantPermissions(['camera', 'notifications'], { origin: server.url });
  const google = createGoogleMock();
  await google.install(context);
  await context.addInitScript(() => {
    // Headless Chromium has no notification UI; report them as allowed and drop them.
    window.Notification = class { static permission = 'granted'; static requestPermission() { return Promise.resolve('granted'); } };
  });

  const page = await context.newPage();
  await page.goto(`${server.url}/demo.html`);
  await page.waitForSelector('#app');
  const box = await (await page.$('#app')).boundingBox();
  const k = box.width / PHONE.width;
  const at = (x, y) => [box.x + x * k, box.y + y * k];

  const stage = {
    page, google, browser,
    /** Clicks at (x, y) in the app's own coordinates. */
    async tap(x, y, { pause = 900 } = {}) {
      await page.mouse.move(...at(x, y), { steps: 12 });
      await page.mouse.down();
      await page.waitForTimeout(80);
      await page.mouse.up();
      await page.waitForTimeout(pause);
    },
    /** Drags from (x1, y1) to (x2, y2), in the app's coordinates. */
    async drag(x1, y1, x2, y2, { pause = 300 } = {}) {
      await page.mouse.move(...at(x1, y1), { steps: 10 });
      await page.mouse.down();
      await page.mouse.move(...at(x2, y2), { steps: 18 });
      await page.mouse.up();
      await page.waitForTimeout(pause);
    },
    async type(text, { delay = 70, pause = 600 } = {}) {
      await page.keyboard.type(text, { delay });
      await page.waitForTimeout(pause);
    },
    async scroll(x, y, dy, { pause = 700 } = {}) {
      await page.mouse.move(...at(x, y));
      for (let i = 0; i < 6; i++) { await page.mouse.wheel(0, (dy * k) / 6); await page.waitForTimeout(60); }
      await page.waitForTimeout(pause);
    },
    caption: (c) => page.evaluate((c) => window.setCaption(c), c),
    consent: (on) => page.evaluate((on) => window.showConsent(on), on),
    wait: (ms) => page.waitForTimeout(ms),
    shot: (path) => page.screenshot({ path, clip: { ...box } }),
    /** Loads [path] (e.g. index.html) into the phone instead of the app. */
    show: (path) => page.evaluate((p) => { document.getElementById('app').src = p; }, path),
    async close() {
      await browser.close();
      server.close();
    },
  };
  return stage;
}
