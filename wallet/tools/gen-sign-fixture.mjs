/**
 * gen-sign-fixture.mjs — proves the byte-level Stellar signing recipe that
 * wallet/app .../stellar/StellarAccount.kt implements, against the REAL
 * @stellar/stellar-sdk (resolved out of /vendor, read-only), and emits a
 * fixture for the Kotlin unit test (SigningFixtureTest).
 *
 * The recipe under test (all offsets on the raw XDR of a TransactionEnvelope
 * that contains exactly one ENVELOPE_TYPE_TX transaction and 0 signatures):
 *
 *   envelope := 00 00 00 02 || tx || 00 00 00 00
 *   payload  := sha256(passphrase) || 00 00 00 02 || tx      (= envelope minus
 *               its trailing zero signature count, prefixed by the network id)
 *   txHash   := sha256(payload)
 *   signed   := 00 00 00 02 || tx || 00 00 00 01
 *               || hint(last 4 bytes of pubkey) || 00 00 00 40 || sig64
 *   sig64    := Ed25519(seed).sign(txHash)      (RFC 8032 — deterministic, so
 *               Kotlin's output must be byte-identical)
 *
 * Everything is asserted here against TransactionBuilder / Keypair / tx.hash()
 * from the vendor-locked SDK; the printed JSON goes verbatim into the Kotlin
 * test. Uses a THROWAWAY fixed seed (public in the test) — never a real account.
 *
 * Run:  node wallet/tools/gen-sign-fixture.mjs
 */

import { createRequire } from "node:module";
import { createHash } from "node:crypto";
import { pathToFileURL } from "node:url";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.dirname(path.dirname(here));
const SDK_DIST = path.join(
  repoRoot, "vendor", "stellar-confidential-token-demo", "packages", "sdk", "dist",
);
const sdkRequire = createRequire(pathToFileURL(path.join(SDK_DIST, "chain", "client.js")).href);
const { Keypair, TransactionBuilder, Account, Networks, Operation, Asset, BASE_FEE } =
  sdkRequire("@stellar/stellar-sdk");

const sha256 = (b) => createHash("sha256").update(b).digest();
const eq = (a, b) => Buffer.compare(Buffer.from(a), Buffer.from(b)) === 0;
const assert = (cond, msg) => { if (!cond) throw new Error(`ASSERT FAILED: ${msg}`); };

// Throwaway deterministic seed — 32 bytes of 0x42. NOT a secret.
const seed = Buffer.alloc(32, 0x42);
const kp = Keypair.fromRawEd25519Seed(seed);

// A representative transaction (payment op; the recipe never looks inside tx).
const source = new Account(kp.publicKey(), "103720918407102567");
const tx = new TransactionBuilder(source, {
  fee: BASE_FEE,
  networkPassphrase: Networks.TESTNET,
})
  .addOperation(Operation.payment({
    destination: "GAJPXAL725N44NMEZ3XIN66ZJ7XURJNLXIDGIOOR7D3E23DIRGLIM73X",
    asset: Asset.native(),
    amount: "12.25",
  }))
  .setTimeout(180)
  .build();

const unsignedB64 = tx.toXDR();
const env = Buffer.from(unsignedB64, "base64");

// --- verify the byte-surgery assumptions against the SDK -------------------
assert(eq(env.subarray(0, 4), [0, 0, 0, 2]), "envelope starts with ENVELOPE_TYPE_TX=2");
assert(eq(env.subarray(env.length - 4), [0, 0, 0, 0]), "unsigned envelope ends with 0 signatures");

const networkId = sha256(Buffer.from(Networks.TESTNET, "utf8"));
const payload = Buffer.concat([networkId, env.subarray(0, env.length - 4)]);
const txHash = sha256(payload);
assert(eq(txHash, tx.hash()), "manual signature-base hash == tx.hash()");

const sig = kp.sign(txHash);
assert(sig.length === 64, "ed25519 signature is 64 bytes");
const hint = kp.rawPublicKey().subarray(28, 32);
assert(eq(hint, kp.signatureHint()), "hint = last 4 bytes of raw public key");

const signedManual = Buffer.concat([
  env.subarray(0, env.length - 4),
  Buffer.from([0, 0, 0, 1]),           // signatures<> count = 1
  hint,
  Buffer.from([0, 0, 0, 0x40]),        // opaque<64> length
  sig,
]);

tx.sign(kp);
const signedSdkB64 = tx.toXDR();
assert(signedManual.toString("base64") === signedSdkB64, "manual signed envelope == SDK signed envelope");

console.log("all byte-surgery assumptions verified against the vendor-locked @stellar/stellar-sdk");
console.log(JSON.stringify({
  seedHex: seed.toString("hex"),
  publicKeyHex: kp.rawPublicKey().toString("hex"),
  accountId: kp.publicKey(),
  networkPassphrase: Networks.TESTNET,
  networkIdHex: networkId.toString("hex"),
  unsignedXdrBase64: unsignedB64,
  txHashHex: txHash.toString("hex"),
  signedXdrBase64: signedSdkB64,
}, null, 2));
