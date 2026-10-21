/** Applies migrations/NNN_*.sql in order, each in its own transaction, once. */

import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";
import type { PgPool } from "./pg";

export async function migrate(pool: PgPool, dir: string, log: (m: string) => void = console.log): Promise<string[]> {
  await pool.query(`CREATE TABLE IF NOT EXISTS schema_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())`);
  const files = (await readdir(dir)).filter((f) => /^\d+_.+\.sql$/.test(f)).sort();
  const applied = new Set((await pool.query<{ name: string }>("SELECT name FROM schema_migrations")).rows.map((r) => r.name));
  const done: string[] = [];
  for (const file of files) {
    if (applied.has(file)) continue;
    const sql = await readFile(join(dir, file), "utf8");
    const client = await pool.connect();
    try {
      // Serialise concurrent deploys.
      await client.query("BEGIN");
      await client.query("SELECT pg_advisory_xact_lock(424242)");
      const again = await client.query("SELECT 1 FROM schema_migrations WHERE name = $1", [file]);
      if (again.rows.length === 0) {
        await client.query(sql);
        await client.query("INSERT INTO schema_migrations (name) VALUES ($1)", [file]);
        done.push(file);
        log(`applied ${file}`);
      }
      await client.query("COMMIT");
    } catch (e) {
      await client.query("ROLLBACK").catch(() => undefined);
      throw new Error(`Migration ${file} failed: ${(e as Error).message}`);
    } finally {
      client.release();
    }
  }
  return done;
}
