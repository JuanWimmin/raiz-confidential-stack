/**
 * Static server for previewing the landing page locally.
 *
 *   node web/serve.mjs        ->  http://localhost:4180
 *
 * Not part of the deliverable: the page itself is a single self-contained
 * index.html that any static host (or the RAIZ landing folder) can serve.
 */
import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const TYPES = { ".html": "text/html; charset=utf-8", ".png": "image/png", ".svg": "image/svg+xml" };

http
  .createServer(async (req, res) => {
    const rel = new URL(req.url, "http://x").pathname;
    const file = path.join(HERE, rel === "/" ? "index.html" : rel.replace(/^\/+/, ""));
    if (!file.startsWith(HERE)) {
      res.writeHead(403).end("403");
      return;
    }
    try {
      const body = await readFile(file);
      res.writeHead(200, {
        "Content-Type": TYPES[path.extname(file).toLowerCase()] ?? "application/octet-stream",
        "Cache-Control": "no-store",
      });
      res.end(body);
    } catch {
      res.writeHead(404).end(`404 ${rel}`);
    }
  })
  .listen(4180, "0.0.0.0", () => console.log("landing preview on http://localhost:4180"));
