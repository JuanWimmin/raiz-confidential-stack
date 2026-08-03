/**
 * Bundles src/main.js -> dist/bench.js (ESM) with esbuild via npx (no local
 * install). The only trick: bare `@noir-lang/*` specifiers in src/main.js must
 * resolve against the VENDOR workspace's installed packages, so we point
 * NODE_PATH (which esbuild honors) at the pnpm directory that holds the whole
 * @noir-lang scope. Everything else in the graph is reached by relative path
 * into the vendor SDK's dist/, whose own bare imports (@noble, @zkpassport,
 * pako, ...) esbuild resolves by walking up from those files as usual.
 *
 * bb.js is intentionally NOT part of the bundle (runtime native-ESM load; see
 * src/main.js). The noir wasm blobs are fetched at runtime too — the bundle
 * stays small.
 *
 * Run:  node build.mjs
 */

import { spawnSync } from "node:child_process";
import { realpathSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const vendorSdk = path.resolve(
  here,
  "..",
  "..",
  "vendor",
  "stellar-confidential-token-demo",
  "packages",
  "sdk",
);

// Resolve the pnpm dir that contains the full @noir-lang scope (noir_js,
// acvm_js, noirc_abi, types) without hardcoding the version-mangled path.
const noirJsReal = realpathSync(path.join(vendorSdk, "node_modules", "@noir-lang", "noir_js"));
const nodePath = path.dirname(path.dirname(noirJsReal)); // .../node_modules

const args = [
  "-y",
  "esbuild",
  path.join(here, "src", "main.js"),
  "--bundle",
  "--format=esm",
  "--platform=browser",
  "--target=es2022",
  "--sourcemap",
  `--outfile=${path.join(here, "dist", "bench.js")}`,
];

console.log(`NODE_PATH=${nodePath}`);
console.log(`npx ${args.join(" ")}`);

const r = spawnSync("npx", args, {
  stdio: "inherit",
  shell: true, // needed to find npx.cmd on Windows
  env: { ...process.env, NODE_PATH: nodePath },
});
process.exit(r.status ?? 1);
