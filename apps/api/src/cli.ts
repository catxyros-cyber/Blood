/**
 * Admin commands:
 *   npm run migrate                       apply database migrations
 *   npm run grant -- <username|uuid> [days] [reason]   give Premium (no days = permanent)
 *   npm run grant -- <username|uuid> revoke
 */

import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { normalizeUuid } from "@bloom/shared";
import { createPool, PgStore } from "./store/pg";
import { migrate } from "./store/migrate";

const apiRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

async function main() {
  const [cmd, ...args] = process.argv.slice(2);
  const url = process.env.DATABASE_URL;
  if (!url) throw new Error("Set DATABASE_URL first.");
  const pool = await createPool(url);
  try {
    if (cmd === "migrate") {
      const done = await migrate(pool, resolve(apiRoot, "migrations"));
      console.log(done.length ? `Applied ${done.length} migration(s).` : "Database is up to date.");
      return;
    }
    if (cmd === "grant") {
      const [who, daysArg, ...reason] = args;
      if (!who) throw new Error("Usage: grant <minecraft username or uuid> [days|revoke] [reason]");
      const store = new PgStore(pool);
      let user = /^[0-9a-f-]{32,36}$/i.test(who) ? await store.getUserByMinecraft(normalizeUuid(who)) : null;
      if (!user) {
        const { rows } = await pool.query<{ minecraft_uuid: string }>("SELECT minecraft_uuid FROM users WHERE lower(username) = lower($1)", [who]);
        if (rows[0]) user = await store.getUserByMinecraft(rows[0].minecraft_uuid);
      }
      if (!user) throw new Error(`No MOTION account for ${who}. They must sign in from the launcher once.`);
      if (daysArg === "revoke") {
        await store.setGrant({ userId: user.id, reason: "revoked", expiresAt: Date.now() - 1 });
        console.log(`Revoked the Premium grant for ${user.username}.`);
        return;
      }
      const days = daysArg ? Number(daysArg) : null;
      if (days !== null && (!Number.isFinite(days) || days <= 0)) throw new Error("days must be a positive number");
      await store.setGrant({ userId: user.id, reason: reason.join(" ") || "manual grant", expiresAt: days ? Date.now() + days * 86_400_000 : null });
      console.log(`Granted Premium to ${user.username}${days ? ` for ${days} days` : " permanently"}.`);
      return;
    }
    throw new Error(`Unknown command "${cmd ?? ""}". Use migrate or grant.`);
  } finally {
    await pool.end();
  }
}

main().catch((e) => {
  console.error((e as Error).message);
  process.exit(1);
});
