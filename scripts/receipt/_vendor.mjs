/**
 * Shared plumbing for the "Mi recibo" (selective disclosure) scripts.
 *
 * Consumes the vendor demo SDK (/vendor/stellar-confidential-token-demo,
 * READ-ONLY) exactly like scripts/ct-flow/_shared.mjs does: import the built
 * dist/ modules by file URL and resolve third-party packages out of the vendor
 * workspace's own node_modules, so we run the exact versions the demo was
 * built against. No vendor file is modified.
 *
 * Also loads the SHARED disclosure artifacts from @ctd/disclosure — the trust
 * anchor both sides of the protocol must agree on (SELECTIVE_DISCLOSURE.md
 * §5.5): prover and verifier read the same compiled circuit, and the verifier
 * additionally pins the shipped verification key byte-for-byte.
 */

import { createRequire } from "node:module";
import { pathToFileURL, fileURLToPath } from "node:url";
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import path from "node:path";
import os from "node:os";

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const REPO_ROOT = path.resolve(HERE, "..", "..");
export const ENV_DEPLOY = path.join(REPO_ROOT, ".env.deploy");
export const DEPLOYMENT_JSON = path.join(REPO_ROOT, "scripts", "ct-flow", "deployment.json");

const VENDOR = path.join(REPO_ROOT, "vendor", "stellar-confidential-token-demo");
const SDK_DIST = path.join(VENDOR, "packages", "sdk", "dist");
const DISCLOSURE_ARTIFACTS = path.join(VENDOR, "packages", "disclosure", "artifacts");
const sdkUrl = (rel) => pathToFileURL(path.join(SDK_DIST, rel)).href;

// ---- vendor SDK modules (read-only imports of the built dist) --------------
export const { ChainClient } = await import(sdkUrl("chain/client.js"));
export const { fetchEvents, eventToJson } = await import(sdkUrl("chain/events.js"));
export const { deriveKeys } = await import(sdkUrl("crypto/keys.js"));
export const { deriveEphemeralRE } = await import(sdkUrl("crypto/poseidon2.js"));
export const { H, scalarMul, pointCoords } = await import(sdkUrl("crypto/grumpkin.js"));
export const { toHex32 } = await import(sdkUrl("crypto/field.js"));
export const { CircuitProver, proverFromArtifact, setUltraHonkBackendLoader } =
  await import(sdkUrl("proving/prover.js"));
export const {
  proveSenderDisclosure, proveRecipientDisclosure,
  verifyDisclosure, DisclosureVerifyError,
  generateRecipientKeys, recipientKeysFromSecret, newDisclosureRequest,
  pointFromJson,
  DISCLOSE_SENDER_CIRCUIT_ID, DISCLOSE_RECIPIENT_CIRCUIT_ID,
} = await import(sdkUrl("disclosure/index.js"));
export const { buildDiscloseSenderWitness } = await import(sdkUrl("witness/disclose-sender.js"));

const sdkRequire = createRequire(pathToFileURL(path.join(SDK_DIST, "chain", "client.js")).href);
export const StellarSdk = sdkRequire("@stellar/stellar-sdk");

// ---- shared disclosure artifacts (§5.5 trust anchor) -----------------------
export function loadDisclosureArtifact(circuitId) {
  return JSON.parse(readFileSync(path.join(DISCLOSURE_ARTIFACTS, `${circuitId}.json`), "utf8"));
}
/** The pinned verification key bytes shipped next to the circuit. */
export function loadPinnedVk(circuitId) {
  const vk = JSON.parse(readFileSync(path.join(DISCLOSURE_ARTIFACTS, `${circuitId}.vk.json`), "utf8"));
  return Uint8Array.from(Buffer.from(vk.vkBase64, "base64"));
}

// ---- multithreaded proving (same technique as scripts/ct-flow/_shared.mjs):
// the SDK's CircuitProver constructs UltraHonkBackend with no options and
// bb.js 0.87.0 then defaults to { threads: 1 }; the documented loader hook
// lets us pass an explicit thread count without touching vendor code. --------
export const THREADS = Math.min(os.cpus().length, 32);
export async function enableThreadedProving() {
  const bbCjsEntry = sdkRequire.resolve("@aztec/bb.js");
  const bbRoot = path.resolve(path.dirname(bbCjsEntry), "..", "..");
  const { UltraHonkBackend } = await import(
    pathToFileURL(path.join(bbRoot, "dest", "node", "index.js")).href
  );
  setUltraHonkBackendLoader(async () =>
    class ThreadedUltraHonkBackend extends UltraHonkBackend {
      constructor(bytecode) {
        super(bytecode, { threads: THREADS });
      }
    });
}

// ---- config / secrets ------------------------------------------------------
export const loadDeployment = () => JSON.parse(readFileSync(DEPLOYMENT_JSON, "utf8"));

export function loadEnvDeploy() {
  if (!existsSync(ENV_DEPLOY)) return {};
  const out = {};
  for (const line of readFileSync(ENV_DEPLOY, "utf8").split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m) out[m[1]] = m[2];
  }
  return out;
}

/** Append/update keys in .env.deploy, preserving everything else. */
export function saveEnvDeploy(update) {
  const env = { ...loadEnvDeploy(), ...update };
  const body =
    "# Session-4 CT flow secrets — TESTNET ONLY, friendbot play-money. NEVER commit.\n" +
    Object.entries(env).map(([k, v]) => `${k}=${v}`).join("\n") + "\n";
  writeFileSync(ENV_DEPLOY, body);
}

// ---- testnet-flakiness armor ----------------------------------------------
// `fatalIf(e)` marks an error as deterministic (e.g. a failed proof check):
// retrying cannot change the outcome, so it propagates immediately.
export async function retry(label, fn, { tries = 3, baseDelayMs = 3000, fatalIf } = {}) {
  let lastErr;
  for (let i = 1; i <= tries; i++) {
    try {
      return await fn();
    } catch (e) {
      if (fatalIf?.(e)) throw e;
      lastErr = e;
      if (i < tries) {
        const delay = baseDelayMs * 2 ** (i - 1);
        console.warn(`  ! ${label} attempt ${i}/${tries} failed (${String(e?.message ?? e).split("\n")[0]}); retrying in ${delay / 1000}s`);
        await new Promise((r) => setTimeout(r, delay));
      }
    }
  }
  throw new Error(`${label} failed after ${tries} attempts: ${lastErr?.message ?? lastErr}`);
}

export const fmtXlm = (stroops) => `${Number(stroops) / 1e7} XLM (${stroops} stroops)`;
