/** Production entry point: `npm start` (after `npm run build`) or `npm run dev`. */

import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { dirname, resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createApp } from "./app";
import { ConfigError, loadConfig, type Config } from "./config";
import { MemoryStore } from "./store/memory";
import { createPool, PgStore } from "./store/pg";
import { migrate } from "./store/migrate";
import type { Store } from "./store/types";
import { Stripe } from "./stripe";

const here = dirname(fileURLToPath(import.meta.url));
/** apps/api, whether running from src/ (tsx) or dist/ (built). */
const apiRoot = resolve(here, "..");

async function main() {
  let config: Config;
  try {
    config = loadConfig();
  } catch (e) {
    if (e instanceof ConfigError) {
      console.error(`Configuration error: ${e.message}`);
      process.exit(1);
    }
    throw e;
  }
  config.websiteDir ??= resolve(apiRoot, "../website/public");
  config.contentDir = resolve(apiRoot, config.contentDir);

  let store: Store;
  if (config.databaseUrl) {
    const pool = await createPool(config.databaseUrl);
    if (process.env.MIGRATE_ON_START !== "0") await migrate(pool, resolve(apiRoot, "migrations"));
    store = new PgStore(pool);
  } else {
    console.warn("DATABASE_URL is not set: using an in-memory store. Data is lost on restart (development only).");
    store = new MemoryStore();
  }

  // Built-in community skins (apps/api/content/skins). Seeding is idempotent: existing ids are kept,
  // so disabling or deleting one on the admin page sticks… until it's re-added here with a new id.
  const builtInSkins = [
    { id: "2f2a9c91-ec58-4bde-9dc7-0740b4f6c311", name: "Tung Tung Tung Sahur", file: "tung-tung-tung-sahur.png", day: 24 },
    { id: "7b1e4d2a-3c5f-4a8e-9d61-5f0c2b8e7a14", name: "Tralalero Tralala", file: "tralalero-tralala.png", day: 25 },
    { id: "c4d9a6f3-2b7e-4f15-8a3c-6e1d0b9f4c27", name: "Bombardiro Crocodilo", file: "bombardiro-crocodilo.png", day: 25 },
    { id: "e8f2b5c1-9a4d-4c6e-b7f3-1d2a8c5e9b36", name: "Ballerina Cappuccina", file: "ballerina-cappuccina.png", day: 25 },
  ];
  for (const s of builtInSkins) {
    await store.createCommunitySkin({
      id: s.id,
      name: s.name,
      model: "classic",
      png: await readFile(join(config.contentDir, "skins", s.file)),
      enabled: true,
      createdAt: Date.UTC(2026, 9, s.day),
    });
  }

  const stripe = config.stripe ? new Stripe(config.stripe.secretKey, config.stripe.apiVersion) : null;
  if (!stripe) console.warn("Stripe is not configured: Premium purchases are disabled.");

  const app = createApp({ config, store, stripe });
  const server = createServer((req, res) => void app.handle(req, res));
  server.headersTimeout = 20_000;
  server.requestTimeout = 30_000;
  server.listen(config.port, config.host, () => {
    console.log(`MOTION API (${config.env}) listening on http://${config.host}:${config.port} — public URL ${config.publicUrl}`);
  });

  const shutdown = async () => {
    console.log("Shutting down…");
    server.close();
    app.close();
    await store.close().catch(() => undefined);
    process.exit(0);
  };
  process.on("SIGINT", () => void shutdown());
  process.on("SIGTERM", () => void shutdown());
}

void main();
