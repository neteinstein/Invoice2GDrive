// Browser-side helpers the Kotlin/Wasm app calls into (see WebGoogleAuthProvider.kt and
// QrScanner.wasmJs.kt). Everything resolves to a JSON string so the Kotlin side only has to
// deal with one interop type.
(function () {
  'use strict';

  // ---- Google Identity Services token client ---------------------------------------------

  function whenGisReady(timeoutMs) {
    return new Promise(function (resolve) {
      var waited = 0;
      (function poll() {
        if (window.google && google.accounts && google.accounts.oauth2) return resolve(true);
        if (waited >= timeoutMs) return resolve(false);
        waited += 100;
        setTimeout(poll, 100);
      })();
    });
  }

  window.faturaAuth = {
    // prompt: 'consent' for the first sign-in, '' to re-issue a token without UI when possible.
    requestToken: function (clientId, scopes, prompt) {
      return whenGisReady(10000).then(function (ready) {
        if (!ready) return JSON.stringify({ error: 'gis_unavailable' });
        return new Promise(function (resolve) {
          try {
            var client = google.accounts.oauth2.initTokenClient({
              client_id: clientId,
              scope: scopes,
              prompt: prompt,
              callback: function (response) { resolve(JSON.stringify(response)); },
              error_callback: function (err) { resolve(JSON.stringify({ error: (err && err.type) || 'unknown' })); }
            });
            client.requestAccessToken();
          } catch (e) {
            // A malformed client ID makes initTokenClient throw instead of calling back.
            resolve(JSON.stringify({ error: 'init_failed' }));
          }
        });
      });
    },
    revoke: function (token) {
      if (window.google && google.accounts && google.accounts.oauth2) google.accounts.oauth2.revoke(token, function () {});
    }
  };

  // ---- QR scanning -----------------------------------------------------------------------

  var JSQR_URL = 'https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.min.js';
  var jsQrPromise = null;

  function loadJsQr() {
    if (window.jsQR) return Promise.resolve(window.jsQR);
    if (!jsQrPromise) {
      jsQrPromise = new Promise(function (resolve, reject) {
        var script = document.createElement('script');
        script.src = JSQR_URL;
        script.onload = function () { resolve(window.jsQR); };
        script.onerror = function () { jsQrPromise = null; reject(new Error('jsQR failed to load')); };
        document.head.appendChild(script);
      });
    }
    return jsQrPromise;
  }

  // Returns a function(source) -> Promise<string|null> using the native BarcodeDetector when the
  // browser has one (Chrome/Edge/Android), jsQR otherwise (Firefox, desktop Safari).
  function makeDecoder() {
    var canvas = document.createElement('canvas');
    var ctx = canvas.getContext('2d', { willReadFrequently: true });
    var nativeDetector = null;
    var ready = Promise.resolve();
    if ('BarcodeDetector' in window) {
      ready = BarcodeDetector.getSupportedFormats().then(function (formats) {
        if (formats.indexOf('qr_code') >= 0) nativeDetector = new BarcodeDetector({ formats: ['qr_code'] });
      }).catch(function () {});
    }
    return function decode(source, width, height) {
      return ready.then(function () {
        if (!width || !height) return null;
        if (nativeDetector) {
          return nativeDetector.detect(source).then(function (codes) { return codes.length ? codes[0].rawValue : null; });
        }
        return loadJsQr().then(function (jsQR) {
          var scale = Math.min(1, 1024 / Math.max(width, height));
          canvas.width = Math.round(width * scale);
          canvas.height = Math.round(height * scale);
          ctx.drawImage(source, 0, 0, canvas.width, canvas.height);
          var image = ctx.getImageData(0, 0, canvas.width, canvas.height);
          var code = jsQR(image.data, image.width, image.height, { inversionAttempts: 'attemptBoth' });
          return code ? code.data : null;
        });
      });
    };
  }

  function el(tag, style, text) {
    var node = document.createElement(tag);
    if (style) node.style.cssText = style;
    if (text) node.textContent = text;
    return node;
  }

  var FONT = "font-family: 'Manrope', system-ui, -apple-system, 'Segoe UI', sans-serif;";
  var BUTTON = FONT + 'border: 0; border-radius: 999px; padding: 12px 20px; font-size: 14px; font-weight: 700; cursor: pointer;';

  window.faturaScanner = {
    // Full-page camera overlay; resolves {text} on a decoded QR, {cancelled:true} when closed.
    scan: function () {
      return new Promise(function (resolve) {
        var decode = makeDecoder();
        var stream = null;
        var finished = false;
        var timer = null;

        var overlay = el('div', 'position: fixed; inset: 0; z-index: 1000; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 22px;' +
          'background: radial-gradient(circle at 50% 30%, #2B2F38, #15171C 60%, #0C0D10); color: #EDEFF2;' + FONT);
        var frame = el('div', 'position: relative; width: min(78vw, 320px); aspect-ratio: 1; border-radius: 22px; overflow: hidden; background: #000;' +
          'box-shadow: 0 0 0 3px #5FA0FF;');
        var video = el('video', 'width: 100%; height: 100%; object-fit: cover;');
        video.setAttribute('playsinline', '');
        video.muted = true;
        var line = el('div', 'position: absolute; left: 8%; right: 8%; top: 50%; height: 2px; background: rgba(95,160,255,.85);');
        frame.appendChild(video);
        frame.appendChild(line);
        var status = el('div', 'max-width: min(84vw, 340px); box-sizing: border-box; text-align: center; padding: 8px 16px; border-radius: 16px; background: rgba(255,255,255,.12); font-size: 13px; line-height: 1.4;', 'Starting camera…');
        var hint = el('div', 'max-width: 280px; text-align: center; color: #C6CAD2; font-size: 13px; line-height: 1.45;',
          "Align the invoice's QR code inside the frame. It fills in automatically.");
        var buttons = el('div', 'display: flex; gap: 10px; margin-top: 8px;');
        var upload = el('button', BUTTON + 'background: #fff; color: #12151B;', 'Upload photo');
        var cancel = el('button', BUTTON + 'background: rgba(255,255,255,.14); color: #fff;', 'Cancel');
        var fileInput = el('input', 'display: none;');
        fileInput.type = 'file';
        fileInput.accept = 'image/*';
        buttons.appendChild(upload);
        buttons.appendChild(cancel);
        [frame, status, hint, buttons, fileInput].forEach(function (n) { overlay.appendChild(n); });
        document.body.appendChild(overlay);

        function finish(result) {
          if (finished) return;
          finished = true;
          if (timer) clearTimeout(timer);
          if (stream) stream.getTracks().forEach(function (t) { t.stop(); });
          overlay.remove();
          resolve(JSON.stringify(result));
        }

        cancel.onclick = function () { finish({ cancelled: true }); };
        upload.onclick = function () { fileInput.click(); };
        fileInput.onchange = function () {
          var file = fileInput.files && fileInput.files[0];
          if (!file) return;
          status.textContent = 'Reading photo…';
          createImageBitmap(file).then(function (bitmap) {
            return decode(bitmap, bitmap.width, bitmap.height);
          }).then(function (text) {
            if (text) finish({ text: text });
            else status.textContent = 'No QR code found in that photo.';
          }).catch(function () { status.textContent = "Couldn't read that photo."; });
          fileInput.value = '';
        };

        function tick() {
          if (finished) return;
          decode(video, video.videoWidth, video.videoHeight).then(function (text) {
            if (text) finish({ text: text });
            else timer = setTimeout(tick, 250);
          }).catch(function () {
            status.textContent = 'QR decoding is unavailable — try Upload photo.';
          });
        }

        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          status.textContent = 'This browser has no camera access here. Upload a photo instead.';
          return;
        }
        navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
          .then(function (s) {
            if (finished) { s.getTracks().forEach(function (t) { t.stop(); }); return; }
            stream = s;
            video.srcObject = s;
            return video.play().then(function () {
              status.textContent = 'Looking for a QR code…';
              tick();
            });
          })
          .catch(function (err) {
            status.textContent = err && err.name === 'NotAllowedError'
              ? 'Camera access was blocked. Allow it in the address bar, or upload a photo.'
              : 'No camera available. Upload a photo instead.';
          });
      });
    },

    // 'granted' | 'denied' | 'prompt' | 'unknown'
    permission: function () {
      if (!navigator.permissions || !navigator.permissions.query) return Promise.resolve('unknown');
      return navigator.permissions.query({ name: 'camera' })
        .then(function (p) { return p.state; })
        .catch(function () { return 'unknown'; });
    },

    requestPermission: function () {
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return Promise.resolve('denied');
      return navigator.mediaDevices.getUserMedia({ video: true, audio: false })
        .then(function (s) { s.getTracks().forEach(function (t) { t.stop(); }); return 'granted'; })
        .catch(function () { return 'denied'; });
    }
  };

  // ---- Invoice photos: IndexedDB storage ---------------------------------------------------

  var dbPromise = null;
  function db() {
    if (!dbPromise) {
      dbPromise = new Promise(function (resolve, reject) {
        var request = indexedDB.open('fatura', 1);
        request.onupgradeneeded = function () { request.result.createObjectStore('photos'); };
        request.onsuccess = function () { resolve(request.result); };
        request.onerror = function () { dbPromise = null; reject(request.error); };
      });
    }
    return dbPromise;
  }

  function tx(mode, work) {
    return db().then(function (database) {
      return new Promise(function (resolve, reject) {
        var t = database.transaction('photos', mode);
        var request = work(t.objectStore('photos'));
        t.oncomplete = function () { resolve(request && request.result !== undefined ? request.result : null); };
        t.onerror = function () { reject(t.error); };
      });
    });
  }

  window.faturaPhotos = {
    put: function (key, base64) { return tx('readwrite', function (s) { return s.put(base64, key); }).then(function () { return 'ok'; }); },
    get: function (key) { return tx('readonly', function (s) { return s.get(key); }); },
    remove: function (key) { return tx('readwrite', function (s) { return s.delete(key); }).then(function () { return 'ok'; }); }
  };

  // ---- Invoice photos: picking -------------------------------------------------------------

  var MAX_EDGE = 2000;

  function readAsBase64(blob) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () { resolve(String(reader.result).split(',')[1]); };
      reader.onerror = function () { reject(reader.error); };
      reader.readAsDataURL(blob);
    });
  }

  // Scales a photo down to MAX_EDGE and re-encodes it as JPEG; also applies EXIF orientation,
  // which createImageBitmap does by default.
  function normalizeImage(file) {
    return createImageBitmap(file).then(function (bitmap) {
      var scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
      var canvas = document.createElement('canvas');
      canvas.width = Math.round(bitmap.width * scale);
      canvas.height = Math.round(bitmap.height * scale);
      canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      return new Promise(function (resolve) { canvas.toBlob(resolve, 'image/jpeg', 0.82); });
    });
  }

  // Solves the 3x3 homography H (h33 = 1) mapping the 4 points `from` onto `to` (flat [x0,y0,...]).
  function homography(from, to) {
    var a = [], b = [], i, j, k;
    for (i = 0; i < 4; i++) {
      var x = from[i * 2], y = from[i * 2 + 1], u = to[i * 2], v = to[i * 2 + 1];
      a.push([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.push(u);
      a.push([0, 0, 0, x, y, 1, -v * x, -v * y]); b.push(v);
    }
    for (i = 0; i < 8; i++) {
      var p = i;
      for (j = i + 1; j < 8; j++) if (Math.abs(a[j][i]) > Math.abs(a[p][i])) p = j;
      if (Math.abs(a[p][i]) < 1e-9) return null;
      var t = a[i]; a[i] = a[p]; a[p] = t; var tb = b[i]; b[i] = b[p]; b[p] = tb;
      for (j = i + 1; j < 8; j++) {
        var f = a[j][i] / a[i][i];
        for (k = i; k < 8; k++) a[j][k] -= f * a[i][k];
        b[j] -= f * b[i];
      }
    }
    var h = new Array(8);
    for (i = 7; i >= 0; i--) {
      var sum = b[i];
      for (j = i + 1; j < 8; j++) sum -= a[i][j] * h[j];
      h[i] = sum / a[i][i];
    }
    return h;
  }

  window.faturaPhoto = {
    // Straightens the quad `corners` ("x,y,..." fractions: TL, TR, BR, BL) of a base64 JPEG into a
    // rectangle. Resolves base64 JPEG, or '' when it can't be done.
    crop: function (base64, corners) {
      var c = String(corners).split(',').map(Number);
      return fetch('data:image/jpeg;base64,' + base64).then(function (r) { return r.blob(); }).then(createImageBitmap).then(function (bitmap) {
        var w = bitmap.width, h = bitmap.height;
        var src = c.map(function (v, i) { return v * (i % 2 === 0 ? w : h); });
        function dist(a, b) { return Math.hypot(src[a * 2] - src[b * 2], src[a * 2 + 1] - src[b * 2 + 1]); }
        var outW = Math.round((dist(0, 1) + dist(3, 2)) / 2), outH = Math.round((dist(0, 3) + dist(1, 2)) / 2);
        if (outW < 16 || outH < 16) return '';
        var inv = homography([0, 0, outW, 0, outW, outH, 0, outH], src);
        if (!inv) return '';
        var from = document.createElement('canvas');
        from.width = w; from.height = h;
        var fctx = from.getContext('2d');
        fctx.drawImage(bitmap, 0, 0);
        var sp = fctx.getImageData(0, 0, w, h).data;
        var to = document.createElement('canvas');
        to.width = outW; to.height = outH;
        var tctx = to.getContext('2d');
        var out = tctx.createImageData(outW, outH), dp = out.data;
        for (var y = 0; y < outH; y++) {
          for (var x = 0; x < outW; x++) {
            var d = inv[6] * x + inv[7] * y + 1;
            var sx = (inv[0] * x + inv[1] * y + inv[2]) / d - 0.5, sy = (inv[3] * x + inv[4] * y + inv[5]) / d - 0.5;
            var x0 = Math.max(0, Math.min(w - 2, Math.floor(sx))), y0 = Math.max(0, Math.min(h - 2, Math.floor(sy)));
            var fx = Math.max(0, Math.min(1, sx - x0)), fy = Math.max(0, Math.min(1, sy - y0));
            var i00 = (y0 * w + x0) * 4, i10 = i00 + 4, i01 = i00 + w * 4, i11 = i01 + 4, o = (y * outW + x) * 4;
            for (var ch = 0; ch < 4; ch++) {
              dp[o + ch] = (sp[i00 + ch] * (1 - fx) + sp[i10 + ch] * fx) * (1 - fy) + (sp[i01 + ch] * (1 - fx) + sp[i11 + ch] * fx) * fy;
            }
          }
        }
        tctx.putImageData(out, 0, 0);
        return new Promise(function (resolve) { to.toBlob(resolve, 'image/jpeg', 0.85); }).then(readAsBase64);
      }).catch(function () { return ''; });
    },

    // Resolves {base64, mimeType} or {cancelled:true}. useCamera asks mobile browsers to open the
    // camera directly; otherwise the file picker also accepts PDFs (invoices received by email).
    pick: function (useCamera) {
      return new Promise(function (resolve) {
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = useCamera ? 'image/*' : 'image/*,application/pdf';
        if (useCamera) input.setAttribute('capture', 'environment');
        input.style.display = 'none';
        var done = false;
        function finish(result) {
          if (done) return;
          done = true;
          input.remove();
          resolve(JSON.stringify(result));
        }
        input.addEventListener('cancel', function () { finish({ cancelled: true }); });
        input.onchange = function () {
          var file = input.files && input.files[0];
          if (!file) return finish({ cancelled: true });
          var isPdf = file.type === 'application/pdf';
          (isPdf ? Promise.resolve(file) : normalizeImage(file))
            .then(readAsBase64)
            .then(function (base64) { finish({ base64: base64, mimeType: isPdf ? 'application/pdf' : 'image/jpeg' }); })
            .catch(function () { finish({ error: "Couldn't read that file." }); });
        };
        document.body.appendChild(input);
        input.click();
      });
    }
  };

  // ---- Notifications -----------------------------------------------------------------------

  window.faturaNotify = {
    permission: function () { return 'Notification' in window ? Notification.permission : 'unsupported'; },
    request: function () {
      if (!('Notification' in window)) return Promise.resolve('unsupported');
      return Notification.requestPermission().then(function (p) { return p; });
    },
    show: function (title, body) {
      if (!('Notification' in window) || Notification.permission !== 'granted') return;
      try { new Notification(title, { body: body, icon: undefined }); } catch (e) { /* some mobile browsers only allow SW notifications */ }
    }
  };
})();
