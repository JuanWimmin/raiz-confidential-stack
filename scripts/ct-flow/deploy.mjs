/**
 * Deploy OUR OWN instance of the OpenZeppelin Confidential Token stack on
 * testnet, using the vendor demo's prebuilt artifacts UNMODIFIED:
 *
 *   - WASM binaries:  vendor/.../packages/sdk/contracts/*.wasm
 *   - verification keys: vendor/.../packages/sdk/circuits/vks/*.vk.bin
 *   - invocation plumbing: the vendor SDK's ChainClient (same calls as the
 *     demo's scripts/deploy.ts, which this script mirrors)
 *
 * Deviations from vendor scripts/deploy.ts (and why):
 *   - No `admin` identity in the global stellar CLI config; the deployer is a
 *     fresh friendbot-funded keypair whose secret lives in .env.deploy.
 *   - No factory / allowlist / blocklist deploys — advanced-mode browser
 *     features we don't use. Verifier + auditor + token + 6 VKs is the full
 *     protocol surface for register/deposit/merge/transfer/withdraw.
 *   - TWO auditor ids instead of one:
 *       id 0 — "custodian" auditor (secret stays private in .env.deploy)
 *       id 1 — "goal view key" (secret INTENDED for deliberate publication:
 *              the Sobre del Barrio public-view-key pattern). Goal accounts
 *              register under id 1; contributor accounts under id 0.
 *   - No `--optimize=false` flag: stellar CLI 23.2.1 does not have it (the
 *     vendor script targets a different CLI version).
 *
 * Run:  node deploy.mjs        (from scripts/ct-flow/)
 */

import { execFileSync } from "node:child_process";
import path from "node:path";

import {
  REPO_ROOT, RPC_URL, PASSPHRASE, XLM_SAC,
  ChainClient, keypairSigner, addressToField,
  randomScalar, toHex32, H, scalarMul, pointToBytes, pointCoords, CIRCUIT_TYPE,
  xdr, Address,
  loadEnvDeploy, saveEnvDeploy, ensureKeypair, friendbotFund, retry,
  saveDeploymentJson, txLedger, explorerContract,
} from "./_shared.mjs";
import { readFileSync } from "node:fs";

const VENDOR_SDK = path.join(REPO_ROOT, "vendor", "stellar-confidential-token-demo", "packages", "sdk");
const WASM = {
  token: path.join(VENDOR_SDK, "contracts", "confidential_token.wasm"),
  verifier: path.join(VENDOR_SDK, "contracts", "confidential_verifier.wasm"),
  auditor: path.join(VENDOR_SDK, "contracts", "confidential_auditor.wasm"),
};
const VKS_DIR = path.join(VENDOR_SDK, "circuits", "vks");

// vk.bin filename → CircuitType discriminant (mirrors vendor scripts/deploy.ts).
const VK_FILES = [
  ["register", CIRCUIT_TYPE.Register],
  ["withdraw", CIRCUIT_TYPE.Withdraw],
  ["transfer", CIRCUIT_TYPE.Transfer],
  ["spender_transfer", CIRCUIT_TYPE.SpenderTransfer],
  ["set_spender", CIRCUIT_TYPE.SetSpender],
  ["revoke_spender", CIRCUIT_TYPE.RevokeSpender],
];

/** Run the stellar CLI, returning trimmed stdout (throws on non-zero exit). */
function stellar(args) {
  return execFileSync("stellar", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
}

function deployContract(secret, wasmPath, ctorArgs) {
  const out = stellar([
    "contract", "deploy",
    "--wasm", wasmPath,
    "--source-account", secret,
    "--rpc-url", RPC_URL,
    "--network-passphrase", PASSPHRASE,
    "--", ...ctorArgs,
  ]);
  const id = out.split(/\s+/).filter(Boolean).pop();
  if (!id?.startsWith("C")) throw new Error(`unexpected deploy output: ${out}`);
  return id;
}

async function main() {
  const env = loadEnvDeploy();

  // 1. Deployer: fresh keypair, friendbot-funded, secret → .env.deploy.
  const deployer = ensureKeypair(env, "CT_DEPLOYER_SECRET");
  saveEnvDeploy(env); // persist before any network call so a crash loses nothing
  console.log(`deployer = ${deployer.publicKey()}`);
  await friendbotFund(deployer.publicKey());

  // 2. Underlying = the network-deterministic XLM SAC (already live on testnet;
  //    same underlying the official demo uses).
  console.log(`underlying (XLM SAC) = ${XLM_SAC}`);

  // 3. Deploy verifier + auditor + token (constructor wires them together).
  const secret = deployer.secret();
  const verifier = await retry("deploy verifier", () =>
    deployContract(secret, WASM.verifier, ["--admin", deployer.publicKey(), "--manager", deployer.publicKey()]));
  console.log(`verifier = ${verifier}`);
  const auditor = await retry("deploy auditor", () =>
    deployContract(secret, WASM.auditor, ["--admin", deployer.publicKey(), "--manager", deployer.publicKey()]));
  console.log(`auditor  = ${auditor}`);

  const client = new ChainClient({
    rpcUrl: RPC_URL,
    networkPassphrase: PASSPHRASE,
    contracts: { token: "", verifier, auditor },
  });
  const deployedAtLedger = await retry("latestLedger", () => client.latestLedger());

  const token = await retry("deploy token", () =>
    deployContract(secret, WASM.token, [
      "--underlying_asset", XLM_SAC,
      "--verifier", verifier,
      "--auditor", auditor,
    ]));
  console.log(`token    = ${token}  (deployed after ledger ${deployedAtLedger})`);
  client.cfg.contracts.token = token;

  const signer = keypairSigner(secret, PASSPHRASE);

  // 4. Register the six circuit verification keys (vendor-built VK bins).
  for (const [name, circuitType] of VK_FILES) {
    const vk = new Uint8Array(readFileSync(path.join(VKS_DIR, `${name}.vk.bin`)));
    const r = await retry(`register VK ${name}`, () =>
      client.invoke(
        verifier,
        "register_verification_key",
        [
          xdr.ScVal.scvU32(circuitType),
          xdr.ScVal.scvBytes(Buffer.from(vk)),
          new Address(deployer.publicKey()).toScVal(),
        ],
        signer,
      ));
    console.log(`  VK ${name} registered (circuit ${circuitType}, ${vk.length}B, tx ${r.hash.slice(0, 8)}…)`);
  }

  // 5. Two auditor Grumpkin keys:
  //    id 0 = custodian (private), id 1 = goal view key (to be published).
  const auditorKeys = [];
  for (const [id, envKey, label] of [
    [0, "CT_AUDITOR0_SECRET_HEX", "custodian (private)"],
    [1, "CT_GOAL_VIEWKEY_SECRET_HEX", "goal view key (to be published)"],
  ]) {
    const k = env[envKey] ? BigInt(env[envKey]) : randomScalar();
    env[envKey] = toHex32(k);
    saveEnvDeploy(env);
    const K = scalarMul(k, H);
    const r = await retry(`register auditor key ${id}`, () =>
      client.invoke(
        auditor,
        "register_key",
        [
          xdr.ScVal.scvU32(id),
          xdr.ScVal.scvBytes(Buffer.from(pointToBytes(K))),
          new Address(deployer.publicKey()).toScVal(),
        ],
        signer,
      ));
    const c = pointCoords(K);
    auditorKeys.push({ id, label, keyXHex: toHex32(c.x), keyYHex: toHex32(c.y) });
    console.log(`  auditor id ${id} registered — ${label} (tx ${r.hash.slice(0, 8)}…)`);
  }

  // 6. Public deployment record.
  const record = {
    network: "testnet",
    rpcUrl: RPC_URL,
    passphrase: PASSPHRASE,
    deployedAtLedger,
    deployer: deployer.publicKey(),
    contracts: { token, verifier, auditor, underlying: XLM_SAC },
    addrF: toHex32(addressToField(token)),
    auditorKeys,
    explorer: {
      token: explorerContract(token),
      verifier: explorerContract(verifier),
      auditor: explorerContract(auditor),
    },
  };
  saveDeploymentJson(record);
  console.log(`\nwrote scripts/ct-flow/deployment.json — token ${token}`);
}

main().catch((e) => {
  console.error("\ndeploy failed:", e);
  process.exit(1);
});
