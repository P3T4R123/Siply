# QR generator

`qrcode-1.5.3.min.js` is a browser bundle of `qrcode@1.5.3` (MIT), generated with `esbuild@0.25.0`:

It includes `dijkstrajs@1.0.3` (MIT). Both licenses are included in this directory.

```sh
esbuild node_modules/qrcode/lib/browser.js --bundle --platform=browser --format=iife --global-name=QRCode --minify --outfile=qrcode-1.5.3.min.js
```

Upstream: https://github.com/soldair/node-qrcode

The bundle is served locally because the previous CDN URL for `qrcode@1.5.3/build/qrcode.min.js` returns 404.
