/**
 * Tiny dependency-free static server for the prover bench.
 *
 * Binds 0.0.0.0:4173 and serves:
 *   /                      -> index.html (this directory)
 *   /dist/*                -> the esbuild bundle (this directory)
 *   /vendor/bb/*           -> bb.js browser build, straight from the vendor
 *                             package (dest/browser: index.js + workers + wasm
 *                             wrappers), never copied — read-only pass-through
 *   /wasm/acvm_js_bg.wasm  -> @noir-lang/acvm_js web wasm (vendor package)
 *   /wasm/noirc_abi_wasm_bg.wasm -> @noir-lang/noirc_abi web wasm (vendor package)
 *   /circuits/<name>.json  -> committed circuit artifacts (vendor SDK)
 *
 * EVERY response carries:
 *   Cross-Origin-Opener-Policy: same-origin
 *   Cross-Origin-Embedder-Policy: credentialless
 * mirroring the demo app's next.config.mjs choice: `credentialless` keeps the
 * page cross-origin isolated while still allowing bb.js's CRS fetch from
 * https://crs.aztec.network without CORP headers on that host.
 *
 * Reminder (spike-findings): crossOriginIsolated additionally needs a SECURE
 * CONTEXT — http://localhost:4173 qualifies (use `adb reverse`), a LAN IP does
 * not (1-thread fallback; still worth measuring).
 *
 * Run:  node serve.mjs
 */

import http from "node:http";
import { createReadStream, realpathSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const VENDOR = path.resolve(HERE, "..", "..", "vendor", "stellar-confidential-token-demo");
const SDK = path.join(VENDOR, "packages", "sdk");

// Resolve vendor asset dirs through the pnpm symlinks (read-only, no copies).
const BB_BROWSER = realpathSync(
  path.join(SDK, "node_modules", "@aztec", "bb.js", "dest", "browser"),
);
const NOIR_SCOPE = path.dirname(
  realpathSync(path.join(SDK, "node_modules", "@noir-lang", "noir_js")),
);
const ACVM_WASM = realpathSync(path.join(NOIR_SCOPE, "acvm_js", "web", "acvm_js_bg.wasm"));
const ABI_WASM = realpathSync(
  path.join(NOIR_SCOPE, "noirc_abi", "web", "noirc_abi_wasm_bg.wasm"),
);
const CIRCUITS = path.join(SDK, "circuits");

const PORT = 4173;
const HOST = "0.0.0.0";

const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".wasm": "application/wasm",
  ".json": "application/json; charset=utf-8",
  ".map": "application/json; charset=utf-8",
  ".txt": "text/plain; charset=utf-8",
};

/** Safe join: resolves inside `root` or returns null (blocks traversal). */
function safeJoin(root, rel) {
  const p = path.normalize(path.join(root, rel));
  return p.startsWith(root + path.sep) || p === root ? p : null;
}

function routeToFile(urlPath) {
  if (urlPath === "/") return path.join(HERE, "index.html");
  if (urlPath === "/wasm/acvm_js_bg.wasm") return ACVM_WASM;
  if (urlPath === "/wasm/noirc_abi_wasm_bg.wasm") return ABI_WASM;
  if (urlPath.startsWith("/vendor/bb/")) {
    // bb's dest/browser is flat: only serve plain basenames.
    return safeJoin(BB_BROWSER, path.basename(urlPath));
  }
  if (urlPath.startsWith("/circuits/")) {
    return safeJoin(CIRCUITS, path.basename(urlPath));
  }
  return safeJoin(HERE, decodeURIComponent(urlPath)); // index.html, /dist/*
}

const server = http.createServer((req, res) => {
  const urlPath = new URL(req.url, "http://x").pathname;
  // COOP/COEP on EVERY response (documents, workers, wasm, errors).
  res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
  res.setHeader("Cross-Origin-Embedder-Policy", "credentialless");

  const file = routeToFile(urlPath);
  let stat;
  try {
    stat = file ? statSync(file) : null;
  } catch {
    stat = null;
  }
  if (!file || !stat?.isFile()) {
    res.writeHead(404, { "Content-Type": "text/plain" });
    res.end(`404 ${urlPath}`);
    console.log(`404 ${req.method} ${urlPath}`);
    return;
  }

  const type = TYPES[path.extname(file).toLowerCase()] ?? "application/octet-stream";
  const cache = urlPath.startsWith("/vendor/") || urlPath.startsWith("/wasm/") || urlPath.startsWith("/circuits/")
    ? "public, max-age=3600" // big immutable-ish vendor assets: let the phone cache them
    : "no-store"; // our own html/bundle: always fresh while iterating
  res.writeHead(200, {
    "Content-Type": type,
    "Content-Length": stat.size,
    "Cache-Control": cache,
  });
  createReadStream(file).pipe(res);
  console.log(`200 ${req.method} ${urlPath} (${stat.size} B)`);
});

server.listen(PORT, HOST, () => {
  console.log(`prover-bench serving on http://${HOST}:${PORT}`);
  console.log(`  bb.js assets:   ${BB_BROWSER}`);
  console.log(`  acvm wasm:      ${ACVM_WASM}`);
  console.log(`  noirc_abi wasm: ${ABI_WASM}`);
  console.log(`  circuits:       ${CIRCUITS}`);
  console.log("Phone (multithreaded): adb reverse tcp:4173 tcp:4173  ->  http://localhost:4173");
  console.log("Phone (1-thread):      http://<LAN-IP>:4173");
});
