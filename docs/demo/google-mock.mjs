// Stands in for Google during the recording: the Identity Services script, the userinfo endpoint,
// and the Drive v3 / Sheets v4 calls GoogleSheetsGateway makes. Nothing leaves the machine, and
// every request is logged so the video can show exactly what the app asked Google for.
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const JSQR = readFileSync(require.resolve('jsqr/dist/jsQR.js'), 'utf8');

export const ACCOUNT = { email: 'reviewer.demo@example.com', name: 'Demo Reviewer' };

// Stub of accounts.google.com/gsi/client: hands the app a token after a short pause, the way the
// real token client calls back once the user approves Google's consent screen.
const GIS_STUB = `window.google = { accounts: { oauth2: {
  initTokenClient: function (config) {
    return { requestAccessToken: function () {
      setTimeout(function () { config.callback({ access_token: 'demo-token', expires_in: 3600, scope: config.scope }); }, 7000);
    } };
  },
  revoke: function (token, done) { done && done(); }
} } };`;

const iso = (daysAgo) => new Date(Date.now() - daysAgo * 86400000).toISOString();

export function createGoogleMock() {
  const state = {
    spreadsheets: [
      { id: 'sheet-despesas', name: 'Despesas 2026', modifiedTime: iso(1) },
      { id: 'sheet-ir', name: 'IRS — deduções', modifiedTime: iso(6) },
      { id: 'sheet-casa', name: 'Orçamento casa', modifiedTime: iso(20) },
    ],
    folders: [
      { id: 'folder-faturas', name: 'Faturas', modifiedTime: iso(2) },
      { id: 'folder-docs', name: 'Documentos', modifiedTime: iso(9) },
      { id: 'folder-scans', name: 'Scans', modifiedTime: iso(30) },
    ],
    tabs: {},          // spreadsheetId -> { tabName: rows[][] }
    uploads: [],
    appended: [],
    log: [],
  };

  const json = (route, body, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', headers: { 'access-control-allow-origin': '*' }, body: JSON.stringify(body) });

  function tabsOf(id) {
    return (state.tabs[id] ??= {});
  }

  function parseRange(encoded) {
    const range = decodeURIComponent(encoded);
    const bang = range.lastIndexOf('!');
    return { tab: range.slice(0, bang).replace(/^'|'$/g, '').replace(/''/g, "'"), cells: range.slice(bang + 1) };
  }

  async function handle(route) {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    if (method === 'OPTIONS') {
      return route.fulfill({ status: 204, headers: {
        'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' } });
    }
    state.log.push(`${method} ${url.host}${url.pathname}`);

    if (url.host === 'accounts.google.com') return route.fulfill({ contentType: 'text/javascript', body: GIS_STUB });
    if (url.host === 'cdn.jsdelivr.net') return route.fulfill({ contentType: 'text/javascript', body: JSQR });
    if (url.pathname === '/oauth2/v3/userinfo') return json(route, ACCOUNT);

    if (url.pathname === '/drive/v3/files') {
      if (method === 'POST') {
        const body = request.postDataJSON();
        const folder = { id: `folder-${state.folders.length + 1}`, name: body.name, modifiedTime: iso(0) };
        state.folders.unshift(folder);
        return json(route, folder);
      }
      const q = url.searchParams.get('q') ?? '';
      const items = q.includes('folder') ? state.folders : state.spreadsheets;
      const name = /name contains '([^']*)'/.exec(q)?.[1]?.toLowerCase();
      return json(route, { files: name ? items.filter((f) => f.name.toLowerCase().includes(name)) : items });
    }

    if (url.pathname === '/upload/drive/v3/files') {
      const id = `photo-${state.uploads.length + 1}`;
      const name = /"name":"([^"]+)"/.exec(request.postDataBuffer()?.toString('latin1') ?? '')?.[1] ?? 'invoice.jpg';
      state.uploads.push({ id, name });
      return json(route, { id, name, webViewLink: `https://drive.google.com/file/d/${id}/view` });
    }

    const sheets = /^\/v4\/spreadsheets(?:\/([^/:]+))?(?::batchUpdate)?(?:\/values\/([^:]+)(:append)?)?$/.exec(url.pathname);
    if (url.host === 'sheets.googleapis.com' && sheets) {
      const [, id, range, append] = sheets;
      if (!id) {
        const title = request.postDataJSON().properties.title;
        const created = { id: `sheet-${state.spreadsheets.length + 1}`, name: title, modifiedTime: iso(0) };
        state.spreadsheets.unshift(created);
        return json(route, { spreadsheetId: created.id, properties: { title } });
      }
      const tabs = tabsOf(id);
      if (url.pathname.endsWith(':batchUpdate')) {
        for (const r of request.postDataJSON().requests) tabs[r.addSheet.properties.title] ??= [];
        return json(route, {});
      }
      if (!range) {
        if (Object.keys(tabs).length === 0) tabs['Faturas'] = [];
        return json(route, { sheets: Object.keys(tabs).map((title) => ({ properties: { title } })) });
      }
      const { tab, cells } = parseRange(range);
      const rows = (tabs[tab] ??= []);
      if (append) {
        const values = request.postDataJSON().values;
        rows.push(...values);
        state.appended.push({ tab, headers: rows[0], values: values[0] });
        return json(route, { updates: { updatedRows: values.length } });
      }
      if (method === 'PUT') {
        rows[0] = request.postDataJSON().values[0];
        return json(route, {});
      }
      if (cells === '1:1') return json(route, { values: rows[0] ? [rows[0]] : [] });
      // A column read for the duplicate check (e.g. H2:H): everything below the header.
      const column = rows.slice(1).map((r) => r[(rows[0] ?? []).findIndex((h) => /atcud/i.test(h))] ?? '');
      return json(route, { values: column.length ? [column] : [] });
    }

    state.log.push(`  (unmocked) ${request.url()}`);
    return json(route, { error: { message: 'Not mocked in the demo' } }, 404);
  }

  return {
    state,
    async install(context) {
      for (const pattern of ['https://accounts.google.com/**', 'https://*.googleapis.com/**', 'https://cdn.jsdelivr.net/**']) {
        await context.route(pattern, handle);
      }
    },
  };
}
