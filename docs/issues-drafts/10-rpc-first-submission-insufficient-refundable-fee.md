# simulateTransaction under-sizes the refundable fee for TTL-extending invocations: ~50% of submissions fail with InsufficientRefundableFee, alternating pass/fail

- Status: DRAFT — do not open online before human review
- Repo it belongs to: stellar/stellar-rpc (simulateTransaction resource/fee estimation)
- Verified: 2026-08-04 against soroban-testnet.stellar.org (protocol 27, latestLedger ~3968500) with stellar CLI 27.1.0 (8e402ea28202950b272fbabc34caad4d2f64fe87)
- Related, not duplicates: stellar/stellar-rpc#253 (expose `SimulationAdjustmentConfig`, which includes a refundable-fee factor) and stellar/stellar-cli#971 (add a CLI knob to raise the refundable fee, "to reduce the risk of transaction failing"). Both treat padding as a user-side workaround; neither reports the underestimate itself.

## Summary

When a contract invocation extends the TTL of several storage entries, the
refundable (rent) fee that `simulateTransaction` returns is frequently too low
by the time the transaction is included, and the operation fails with
`InvokeHostFunction(InsufficientRefundableFee)`. The caller changes nothing
and retries; the retry succeeds. Over a run of consecutive identical
invocations the outcome alternates almost perfectly failure/success.

## Repro steps (self-contained)

1. Build and deploy this contract (soroban-sdk 22.x, `wasm32v1-none`). It does
   nothing but bump rent on two persistent entries plus the instance, and emit
   one event — the shape that triggers it:

```rust
#![no_std]
use soroban_sdk::{contract, contractimpl, symbol_short, Env, Symbol};

const THRESHOLD: u32 = 518_400; // ~30 days
const EXTEND_TO: u32 = 518_400;
const A: Symbol = symbol_short!("A");
const B: Symbol = symbol_short!("B");

#[contract]
pub struct Rent;

#[contractimpl]
impl Rent {
    pub fn seed(env: Env) {
        env.storage().persistent().set(&A, &1u32);
        env.storage().persistent().set(&B, &2u32);
    }

    pub fn bump(env: Env) {
        env.storage().persistent().extend_ttl(&A, THRESHOLD, EXTEND_TO);
        env.storage().persistent().extend_ttl(&B, THRESHOLD, EXTEND_TO);
        env.storage().instance().extend_ttl(THRESHOLD, EXTEND_TO);
        env.events().publish((symbol_short!("bumped"),), 1u32);
    }
}
```

```
stellar keys generate --fund --network testnet feeprobe
stellar contract deploy --wasm rent.wasm --source feeprobe --network testnet
stellar contract invoke --id <ID> --source feeprobe --network testnet -- seed
```

2. Invoke `bump` repeatedly, letting the CLI simulate each time (no fee
   overrides, no `--fee` padding):

```
for i in $(seq 1 8); do
  stellar contract invoke --id <ID> --source feeprobe --network testnet -- bump
done
```

## Expected

Each submission carries the resource fee that the immediately preceding
`simulateTransaction` returned for that exact transaction, so it should not
underpay the refundable portion.

## Observed

4 of 8 consecutive attempts failed, in a strict alternating pattern
(OK, FAIL, OK, FAIL, OK, FAIL, OK, FAIL). Verbatim:

```
ℹ️  Simulating transaction…
ℹ️  Signing transaction: dcc4b4ac4db534f3de91fa56d03ef2d897991173146f595ed3330af7c09dd88d
🌎 Sending transaction…
❌ error: transaction submission failed: Some(
    TransactionResult {
        fee_charged: 4740,
        result: TxFailed(
            VecM(
                [
                    OpInner(
                        InvokeHostFunction(
                            InsufficientRefundableFee,
                        ),
                    ),
                ],
            ),
        ),
        ext: V0,
    },
)
```

The failed attempts are charged the fee (4740 stroops here) and produce no
state change. An earlier observation of the same failure on a different
contract charged 5090 stroops, so the amount tracks the transaction rather
than being a constant.

Live examples on testnet (contract
`CCPVH5DFWQGF22KT5VCBYTDHP6DCYA5R27DSHV7LCHDQQYA53IQILY7R`):

- failed: `dcc4b4ac4db534f3de91fa56d03ef2d897991173146f595ed3330af7c09dd88d`
- failed: `ad3f13d246c9319b2f9d0c4d8863e6a93432081202cb298af69ddeed4a30a20f`
- succeeded: `f074d337b1c211f17a8255c9fbbcfd42ec9fd6ff8328ae4acac4abe200eef09d`
- succeeded: `42c549d5c5aec575624407a4c8ff32b899334218a64cd169c3e9fe8290f3a9fc`

## Hypothesis (offered only as a lead)

The alternation is suspicious. After a successful `bump`, the entries sit at
the maximum TTL, so the next simulation prices the rent bump at (nearly)
nothing; by the time that transaction is applied the ledger has advanced and a
small amount of rent is genuinely due, so the submitted refundable fee is
short. The failed attempt changes no state, so the following simulation again
sees entries that need a real bump, prices it generously, and succeeds. If
that is what is happening, the estimate is being computed against the
simulation ledger with no allowance for the ledgers that pass before
inclusion.

## Suggested fix / question

Either size the refundable fee with headroom for simulation-to-inclusion
ledger drift, or document that rent-extending invocations need caller-side
padding and treat `InsufficientRefundableFee` as an expected, retryable
outcome. Right now the default path of "simulate, then submit what simulation
said" is a coin flip for this transaction shape, which is a rough edge for any
tool that bumps TTLs on a schedule. Happy to provide the full envelopes and
simulation responses for a failing/succeeding pair if useful.
