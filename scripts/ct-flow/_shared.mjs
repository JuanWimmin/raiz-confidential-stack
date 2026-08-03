/**
 * Shared plumbing for the Session-4 CT command-line flow.
 *
 * Consumes the vendor demo SDK (/vendor/stellar-confidential-token-demo,
 * READ-ONLY) exactly like /scripts/prover-bench does: import the built dist/
 * modules by relative path and resolve third-party packages (stellar-sdk,
 * bb.js) out of the vendor workspace's own node_modules via createRequire, so
 * we run the exact versions the demo was built against.
 *
 * NOTE on @stellar/stellar-sdk instance identity: its package.json "exports"
 * maps both the import and require conditions to the same CJS file
 * (./lib/index.js), so the copy we obtain here IS the module instance the SDK
 * dist uses internally — XDR objects can safely cross the boundary.
 *
 * Secrets (Stellar S-keys, confidential scalars, auditor Grumpkin secrets)
 * live ONLY in C:\SP_WorkShop\.env.deploy (gitignored). Public ids live in
 * scripts/ct-flow/deployment.json.
 */

import { createRequire } from "node:module";
import { pathToFileURL, fileURLToPath } from "node:url";
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import path from "node:path";
import os from "node:os";

// ---------------------------------------------------------------------------
// Locations
// ---------------------------------------------------------------------------
const HERE = path.dirname(fileURLToPath(import.meta.url));
export const REPO_ROOT = path.resolve(HERE, "..", "..");
export const ENV_DEPLOY = path.join(REPO_ROOT, ".env.deploy");
export const DEPLOYMENT_JSON = path.join(HERE, "deployment.json");
export const RUN_LOG_JSON = path.join(HERE, "run-log.json");

const SDK_DIST = path.join(
  REPO_ROOT,
  "vendor", "stellar-confidential-token-demo", "packages", "sdk", "dist",
);
const sdkUrl = (rel) => pathToFileURL(path.join(SDK_DIST, rel)).href;

// ---------------------------------------------------------------------------
// Vendor SDK modules (all read-only imports of the built dist)
// ---------------------------------------------------------------------------
export const { ChainClient, keypairSigner } = await import(sdkUrl("chain/client.js"));
export const { submitRegister, submitDeposit, submitMerge, submitTransfer, submitWithdraw } =
  await import(sdkUrl("chain/contract.js"));
export const { fetchEvents, eventToJson } = await import(sdkUrl("chain/events.js"));
export const { addressToField } = await import(sdkUrl("crypto/address.js"));
export const { randomScalar, toHex32 } = await import(sdkUrl("crypto/field.js"));
export const { deriveKeys } = await import(sdkUrl("crypto/keys.js"));
export const { H, scalarMul, pointToBytes, pointCoords, commit } = await import(sdkUrl("crypto/grumpkin.js"));
export const { CIRCUIT_TYPE } = await import(sdkUrl("crypto/constants.js"));
export const { CircuitProver, setUltraHonkBackendLoader } = await import(sdkUrl("proving/prover.js"));
export const { loadCircuit } = await import(sdkUrl("proving/artifacts.js"));
export const { StateEngine } = await import(sdkUrl("state/engine.js"));
export const { MemoryStore } = await import(sdkUrl("state/store.js"));
export const { buildRegisterWitness } = await import(sdkUrl("witness/register.js"));
export const { buildTransferWitness } = await import(sdkUrl("witness/transfer.js"));
export const { buildWithdrawWitness } = await import(sdkUrl("witness/withdraw.js"));
export const {
  auditTransfer, auditTransferSenderChannel, auditTransferRecipientChannel,
  auditWithdraw, auditorPublicKey,
} = await import(sdkUrl("auditor/decrypt.js"));

// @stellar/stellar-sdk — same instance as the dist (see header note).
const sdkRequire = createRequire(pathToFileURL(path.join(SDK_DIST, "chain", "client.js")).href);
export const StellarSdk = sdkRequire("@stellar/stellar-sdk");
export const { Keypair, xdr, Address, nativeToScVal } = StellarSdk;

// ---------------------------------------------------------------------------
// Multithreaded proving.
// The SDK's CircuitProver constructs `new UltraHonkBackend(bytecode)` with no
// options and bb.js 0.87.0 defaults to { threads: 1 } (measured 2-3x slower —
// see friction-report.md). The prover exposes setUltraHonkBackendLoader as its
// documented override hook; we use it to pass an explicit thread count without
// touching vendor code (same technique as /scripts/prover-bench/bench-node.mjs).
// ---------------------------------------------------------------------------
export const THREADS = Math.min(os.cpus().length, 32);

export async function enableThreadedProving() {
  const bbCjsEntry = sdkRequire.resolve("@aztec/bb.js"); // <root>/dest/node-cjs/index.js
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

// ---------------------------------------------------------------------------
// Network constants
// ---------------------------------------------------------------------------
export const RPC_URL = "https://soroban-testnet.stellar.org";
export const PASSPHRASE = StellarSdk.Networks.TESTNET;
export const FRIENDBOT = "https://friendbot.stellar.org";
/** The XLM Stellar Asset Contract on testnet (network-deterministic id). */
export const XLM_SAC = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC";

// ---------------------------------------------------------------------------
// Testnet-flakiness armor: retry with exponential backoff (project rule 3).
// ---------------------------------------------------------------------------
export async function retry(label, fn, { tries = 4, baseDelayMs = 3000 } = {}) {
  let lastErr;
  for (let i = 1; i <= tries; i++) {
    try {
      return await fn();
    } catch (e) {
      lastErr = e;
      const msg = String(e?.message ?? e).split("\n")[0];
      if (i < tries) {
        const delay = baseDelayMs * 2 ** (i - 1);
        console.warn(`  ! ${label} attempt ${i}/${tries} failed (${msg}); retrying in ${delay / 1000}s`);
        await sleep(delay);
      }
    }
  }
  throw new Error(`${label} failed after ${tries} attempts: ${lastErr?.message ?? lastErr}`);
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Fund a G-address via friendbot (400 = already funded → fine). */
export async function friendbotFund(pubkey) {
  await retry(`friendbot ${pubkey.slice(0, 5)}…`, async () => {
    const res = await fetch(`${FRIENDBOT}/?addr=${encodeURIComponent(pubkey)}`);
    if (!res.ok && res.status !== 400) throw new Error(`friendbot HTTP ${res.status}`);
  });
}

// ---------------------------------------------------------------------------
// .env.deploy — flat KEY=VALUE store for secrets (gitignored)
// ---------------------------------------------------------------------------
export function loadEnvDeploy() {
  if (!existsSync(ENV_DEPLOY)) return {};
  const out = {};
  for (const line of readFileSync(ENV_DEPLOY, "utf8").split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m) out[m[1]] = m[2];
  }
  return out;
}

export function saveEnvDeploy(update) {
  const env = { ...loadEnvDeploy(), ...update };
  const body =
    "# Session-4 CT flow secrets — TESTNET ONLY, friendbot play-money. NEVER commit.\n" +
    Object.entries(env).map(([k, v]) => `${k}=${v}`).join("\n") + "\n";
  writeFileSync(ENV_DEPLOY, body);
}

/** Get-or-create a Stellar keypair persisted under `envKey` in .env.deploy. */
export function ensureKeypair(env, envKey) {
  if (env[envKey]) return Keypair.fromSecret(env[envKey]);
  const kp = Keypair.random();
  env[envKey] = kp.secret();
  return kp;
}

/** Get-or-create a confidential scalar (hex) persisted under `envKey`. */
export function ensureScalar(env, envKey) {
  if (env[envKey]) return BigInt(env[envKey]);
  const s = randomScalar();
  env[envKey] = toHex32(s);
  return s;
}

// ---------------------------------------------------------------------------
// Deployment record (public ids only)
// ---------------------------------------------------------------------------
export function loadDeploymentJson() {
  if (!existsSync(DEPLOYMENT_JSON)) {
    throw new Error(`no ${DEPLOYMENT_JSON}; run deploy.mjs first`);
  }
  return JSON.parse(readFileSync(DEPLOYMENT_JSON, "utf8"));
}

export function saveDeploymentJson(d) {
  writeFileSync(DEPLOYMENT_JSON, JSON.stringify(d, null, 2) + "\n");
}

/** Ledger a confirmed tx landed in (for run logs / explorer URLs). */
export async function txLedger(client, hash) {
  const res = await retry(`getTransaction ${hash.slice(0, 8)}…`, () => client.server.getTransaction(hash));
  return res.ledger;
}

export const explorerTx = (hash) => `https://stellar.expert/explorer/testnet/tx/${hash}`;
export const explorerContract = (id) => `https://stellar.expert/explorer/testnet/contract/${id}`;

/** BigInt-tolerant JSON stringifier for run logs. */
export const jsonify = (obj) =>
  JSON.stringify(obj, (_k, v) => (typeof v === "bigint" ? v.toString() : v), 2);
