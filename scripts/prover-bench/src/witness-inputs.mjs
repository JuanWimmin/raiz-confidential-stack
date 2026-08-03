/**
 * Synthetic witness inputs for the three proving circuits of the Confidential
 * Token demo (register / withdraw / transfer). No chain, no Freighter, no
 * persistence: fresh random keys and fixed toy amounts, exactly the recipe the
 * vendor SDK's own headless test (packages/sdk/test/prove.mjs) uses to prove
 * without any wallet.
 *
 * Shared by bench-node.mjs (Node baseline) and src/main.js (browser page), so
 * both environments prove the byte-identical kind of witness.
 *
 * Imports reach into the vendor SDK's built dist/ (read-only, imported as-is).
 */

import { deriveKeys } from "../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/crypto/keys.js";
import { addressToField } from "../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/crypto/address.js";
import { G, scalarMul } from "../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/crypto/grumpkin.js";
import { randomScalar } from "../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/crypto/field.js";
import { buildRegisterWitness } from "../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/witness/register.js";
import { buildWithdrawWitness } from "../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/witness/withdraw.js";
import { buildTransferWitness } from "../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/witness/transfer.js";

/**
 * The deployed testnet CT wrapper (deployment.ts:79 in the demo app). Only
 * used as the strkey the keys are domain-bound to; the bench never touches
 * the network, any well-formed 56-char strkey yields the same proving cost.
 */
export const TOKEN_STRKEY = "CBF64DEOVQAXJFBSNGFEUT2AH4H7K5JBY3ZYJ5GVEINMNSDISWRG5N3F";

const addrF = addressToField(TOKEN_STRKEY);

/** A random Grumpkin public key standing in for an auditor-registry entry. */
function syntheticAuditorKey() {
  return scalarMul(randomScalar(), G);
}

/** register: prove knowledge of sk behind Y and PVK. */
export function registerInputs() {
  const keys = deriveKeys(randomScalar(), addrF);
  return buildRegisterWitness(keys).inputs;
}

/** withdraw: debit 400 of 1000 from a synthetic spendable balance. */
export function withdrawInputs() {
  const keys = deriveKeys(randomScalar(), addrF);
  return buildWithdrawWitness({
    keys,
    v: 1000n,
    r: 0n,
    amount: 400n,
    kAudS: syntheticAuditorKey(),
  }).inputs;
}

/** transfer: move 250 of 1000 to a synthetic recipient, dual auditor channels. */
export function transferInputs() {
  const sender = deriveKeys(randomScalar(), addrF);
  const recipient = deriveKeys(randomScalar(), addrF);
  return buildTransferWitness({
    keys: sender,
    v: 1000n,
    r: 0n,
    amount: 250n,
    pvkB: recipient.PVK,
    kAudR: syntheticAuditorKey(),
    kAudS: syntheticAuditorKey(),
  }).inputs;
}

/** Ordered map used by both benches. */
export const INPUT_BUILDERS = {
  register: registerInputs,
  withdraw: withdrawInputs,
  transfer: transferInputs,
};
