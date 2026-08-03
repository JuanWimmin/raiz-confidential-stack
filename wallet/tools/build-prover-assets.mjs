/**
 * build-prover-assets.mjs — packages the CT proving stack into the app's
 * assets so the WebView needs NO dev server (WebViewAssetLoader serves
 * https://appassets.androidplatform.net/prover/* straight from the APK).
 *
 * Two jobs (recipe proven in scripts/prover-bench/{build.mjs,serve.mjs}):
 *
 *  1. COPY runtime assets from /vendor (read-only source, never modified):
 *       vendor/bb/       @aztec/bb.js dest/browser — native-ESM index.js,
 *                        main/thread workers, wasm-wrapper JS (wasm embedded
 *                        as gzipped data: URLs), licenses. NEVER bundled:
 *                        bb.js resolves its workers as sibling files of
 *                        import.meta.url.
 *       wasm/            @noir-lang acvm_js + noirc_abi web wasm blobs.
 *       circuits/        register/transfer/withdraw circuit JSON (vendor SDK).
 *
 *  2. BUNDLE our shim (assets/prover/raiz-shim.js, committed) with esbuild
 *     into assets/prover/dist/prover.js. Bare `@noir-lang/*` imports resolve
 *     against the VENDOR workspace's installed packages via NODE_PATH
 *     (esbuild honors it); the shim's relative imports into the vendor SDK
 *     dist/ resolve normally.
 *
 * Everything this script WRITES is generated and gitignored (wallet/.gitignore
 * — see "Generated prover assets"); raiz-shim.js and index.html in the same
 * directory are committed sources. APK cost: ~14 MB of assets.
 *
 * Prereq: /vendor/stellar-confidential-token-demo has its node_modules
 * installed (Session 0 did `npx -y pnpm@10.33.0 install` there).
 *
 * Run:  node wallet/tools/build-prover-assets.mjs
 */

import { spawnSync } from "node:child_process";
import { cpSync, mkdirSync, realpathSync, rmSync, statSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url)); // wallet/tools
const walletDir = path.dirname(here);
const repoRoot = path.dirname(walletDir);
const proverDir = path.join(walletDir, "app", "src", "main", "assets", "prover");
const sdk = path.join(repoRoot, "vendor", "stellar-confidential-token-demo", "packages", "sdk");

// Resolve vendor asset dirs through the pnpm symlinks (same as prover-bench).
const noirJsReal = realpathSync(path.join(sdk, "node_modules", "@noir-lang", "noir_js"));
const noirScope = path.dirname(noirJsReal);
// Two roots for esbuild's bare-import resolution (NODE_PATH is a list):
//  - the pnpm-store node_modules that holds @noir-lang/*
//  - the vendor SDK package's own node_modules, where @stellar/stellar-sdk
//    lives (raiz-shim.js imports it directly since Session 5 step 5; the
//    vendor dist files reach the same real file by normal walk-up, so the
//    bundle contains a single instance)
const nodePath = [path.dirname(noirScope), path.join(sdk, "node_modules")].join(path.delimiter);
const bbBrowser = realpathSync(path.join(sdk, "node_modules", "@aztec", "bb.js", "dest", "browser"));
const acvmWasm = realpathSync(path.join(noirScope, "acvm_js", "web", "acvm_js_bg.wasm"));
const abiWasm = realpathSync(path.join(noirScope, "noirc_abi", "web", "noirc_abi_wasm_bg.wasm"));
const circuitsDir = path.join(sdk, "circuits");

const CIRCUITS = ["register", "transfer", "withdraw"]; // deposit/merge need no proof

// ---------------------------------------------------------------------------
// 1. Copy vendor runtime assets (wipe generated dirs first; committed files
//    raiz-shim.js / index.html live directly in proverDir and are untouched).
// ---------------------------------------------------------------------------
for (const d of ["dist", "vendor", "wasm", "circuits"]) {
  rmSync(path.join(proverDir, d), { recursive: true, force: true });
}

cpSync(bbBrowser, path.join(proverDir, "vendor", "bb"), { recursive: true });
mkdirSync(path.join(proverDir, "wasm"), { recursive: true });
cpSync(acvmWasm, path.join(proverDir, "wasm", "acvm_js_bg.wasm"));
cpSync(abiWasm, path.join(proverDir, "wasm", "noirc_abi_wasm_bg.wasm"));
mkdirSync(path.join(proverDir, "circuits"), { recursive: true });
for (const name of CIRCUITS) {
  cpSync(path.join(circuitsDir, `${name}.json`), path.join(proverDir, "circuits", `${name}.json`));
}

// ---------------------------------------------------------------------------
// 2. Bundle the shim.
// ---------------------------------------------------------------------------
const args = [
  "-y",
  "esbuild",
  path.join(proverDir, "raiz-shim.js"),
  "--bundle",
  "--format=esm",
  "--platform=browser",
  "--target=es2022",
  "--sourcemap",
  // Session 5 step 5 additions for the RaizChain builders:
  //  - @stellar/stellar-sdk resolves via its package.json "browser" field to
  //    the self-contained dist/stellar-sdk.min.js (UMD -> esbuild CJS interop).
  //  - inject provides the free `Buffer` global the vendor dist modules expect
  //    (see buffer-inject.js).
  //  - global=globalThis: defensive; webpack-built UMD sometimes probes it.
  `--inject:${path.join(here, "buffer-inject.js")}`,
  "--define:global=globalThis",
  `--outfile=${path.join(proverDir, "dist", "prover.js")}`,
];
console.log(`NODE_PATH=${nodePath}`);
console.log(`npx ${args.join(" ")}`);
const r = spawnSync("npx", args, {
  stdio: "inherit",
  shell: true, // needed to find npx.cmd on Windows
  env: { ...process.env, NODE_PATH: nodePath },
});
if (r.status !== 0) process.exit(r.status ?? 1);

// ---------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------
let total = 0;
function walk(dir, rel = "") {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, `${rel}${e.name}/`);
    else {
      const kb = Math.round(statSync(p).size / 1024);
      total += kb;
      console.log(`  ${String(kb).padStart(6)} KB  ${rel}${e.name}`);
    }
  }
}
console.log(`\nassets/prover/ contents:`);
walk(proverDir);
console.log(`  ${String(total).padStart(6)} KB  TOTAL`);
console.log(`\nOK — rebuild the APK to pick the assets up.`);
