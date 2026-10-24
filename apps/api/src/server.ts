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

  await store.createCommunitySkin({
    id: "2f2a9c91-ec58-4bde-9dc7-0740b4f6c311",
    name: "Tung Tung Tung Sahur",
    model: "classic",
    png: await readFile(join(config.contentDir, "skins", "tung-tung-tung-sahur.png")),
    enabled: true,
    createdAt: Date.UTC(2026, 9, 24),
  });

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
