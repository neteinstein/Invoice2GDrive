# Demo video

[`invoice2sheet-demo.mp4`](invoice2sheet-demo.mp4) (1080p, about 2 minutes) shows the web app from
first launch to a saved invoice. It's meant for Google's OAuth verification reviewers and anyone
else who wants to see the app without signing in. Captions next to the app explain what each step
does with the user's Google data.

1. Welcome screen and the **Connect your Google Account** screen, which lists the requested scopes
   (`spreadsheets`, `drive`, `email`, `profile`) and links the Terms and Privacy Policy.
2. Google sign-in with the **Web application** OAuth client.
3. Picking a spreadsheet and a Drive folder (`drive` scope: lists names and edit dates only).
4. Scanning the invoice QR code with the camera, photographing and cropping the invoice.
5. Reviewing the fields and saving: a duplicate check, the photo upload to the chosen folder, and
   the row appended to the sheet. The video shows the exact row sent to the Sheets API.
6. Settings: the connected account, **Sign out**, and the permissions.

## How it's made

`record.mjs` drives the production web build in headless Chromium with Playwright, inside a 16:9
stage (`stage.mjs`), and encodes a DevTools screencast with ffmpeg. No Google account is used:

- `google-mock.mjs` intercepts the Google Identity Services script, the userinfo endpoint and the
  Drive v3 / Sheets v4 calls, and answers them with sample data (`reviewer.demo@example.com`, a few
  spreadsheets and folders).
- Google's own account picker and consent screen can't be shown without a real account, so the
  recording shows a labelled placeholder listing the permissions instead.
- `fixtures.mjs` generates a made-up invoice with a valid Portuguese fiscal QR code, and a camera
  feed for Chromium's fake video capture device.

To re-record (needs Node 18+, ffmpeg and a JDK):

```bash
cd docs/demo
npm install
node record.mjs --build   # builds the web app with a placeholder OAuth client ID, then records
node record.mjs           # re-records using the existing build
```

The tap coordinates in `record.mjs` match the current screens; update them if the layout changes.
