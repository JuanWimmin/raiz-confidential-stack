/**
 * esbuild --inject shim: the vendor SDK's dist modules (chain/payload.js etc.)
 * reference a free `Buffer` global, which exists in Node but not in the
 * browser; the @stellar/stellar-sdk BROWSER bundle keeps its own copy private.
 * Injecting this file makes esbuild rewrite every free `Buffer` reference in
 * the bundle to this export — the same feross/buffer polyfill the Stellar SDK
 * itself bundles, resolved read-only out of the vendor workspace's pnpm store
 * (version pinned by the vendor lockfile).
 */
import { Buffer } from "../../vendor/stellar-confidential-token-demo/node_modules/.pnpm/buffer@6.0.3/node_modules/buffer/index.js";

globalThis.Buffer ||= Buffer; // for any runtime lookup that dodges the rewrite

export { Buffer };
