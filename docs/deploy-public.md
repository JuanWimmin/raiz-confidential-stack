# Publishing Raiz Memory

Two routes, both verified on 2026-08-03. The honest summary first: **the
container is the reproducible route, the tunnel is the convenient one.** A
judge who wants to check our claims should use the container; the tunnel exists
so a phone (or a curious reviewer during the event) can hit a live instance
without building anything.

## Route 1 — container (reproducible, verified)

```bash
cd raiz-memory
cp .env.example .env          # set RPC_URL and CONTRACT_IDS
docker compose up --build     # serves on http://localhost:8090
curl localhost:8090/health
curl localhost:8090/coverage
```

Verified: the image builds from a clean context, the container starts, and
`/health` and `/coverage` answer with the contracts configured.

Three bugs were fixed in this setup on 2026-08-03, all of which would have hit
anyone following the quickstart:

- `Cargo.lock` was not copied into the build stage, so the image resolved a
  fresh dependency tree instead of ours. It is copied now and the build runs
  `--locked`.
- The base image was `rust:1.83-slim`, too old for this dependency tree.
- `docker-compose.yml` published `8090:8090` while `env_file: .env` fed the
  process whatever `PORT` the local `.env` carried (ours says 8091), so the
  container was unreachable on the mapped port. Compose now overrides `PORT`
  and `DATABASE_URL` explicitly, and the image defaults to 8090.

## Route 2 — public tunnel (convenient, ephemeral)

We have no VM, so the public instance is a Cloudflare quick tunnel from the dev
machine. One command brings up the indexer, waits for it to be healthy, prints
what it actually holds, opens the tunnel and verifies the public URL answers
before announcing it:

```powershell
pwsh scripts/serve-public.ps1
```

Manually, if you prefer:

```powershell
winget install --id Cloudflare.cloudflared
& 'C:\Program Files (x86)\cloudflared\cloudflared.exe' tunnel --url http://localhost:8091
```

It prints a `https://<random-words>.trycloudflare.com` URL that proxies straight
to the local indexer. Verified twice on 2026-08-03/04 serving real data:
`/health`, `/coverage` and `/events` all answer over the public URL, backed by
the SQLite file that held 4,138 indexed events across four testnet contracts at
the second check.

That instance is also the strongest single piece of evidence for the whole
thesis: it serves the official CT demo wrapper's events from ledger **3837609**,
while the public RPC's retention floor had already risen to **3838325**. Those
events are gone from the network and present here — which is the entire point of
the component. A clone started today gets the wrapper's history only from the
current floor (73 events versus our 81), and `/coverage` says so rather than
hiding it.

**Its limitations, stated plainly.** A quick tunnel is anonymous and free, and
it behaves accordingly:

- the hostname changes every time `cloudflared` restarts,
- it dies when the dev machine sleeps or loses network,
- there is no uptime guarantee whatsoever.

So the URL is **not** a permanent service, and the README must not present it as
one. It is published as *live while we are at the venue*, next to the container
instructions that work forever. Promising a stable endpoint we cannot keep up
would be the same kind of dishonesty as an indexer that lies about its coverage.

If a stable URL is needed later, the same container runs unchanged on any
host with Docker; only the base URL in the wallet's settings changes.

## Pointing the wallet at an instance

The wallet reads its event source from a setting, which is the whole point of
the architecture — switching it live is the demo's central scene:

- local development: `http://localhost:8091` with `adb reverse tcp:8091 tcp:8091`
- public tunnel: the `https://<...>.trycloudflare.com` URL
- forgetful-RPC simulation: the same base URL with `?source=rpc-simulation`
  (see `raiz-memory/README.md`)
