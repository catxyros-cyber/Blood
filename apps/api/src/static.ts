/** Serves the companion website (apps/website/public) with safe paths and sensible caching. */

import { readFile, stat } from "node:fs/promises";
import { extname, join, normalize, resolve, sep } from "node:path";
import { HttpError, type Reply } from "./http";

const TYPES: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".webp": "image/webp",
  ".txt": "text/plain; charset=utf-8",
  ".woff2": "font/woff2",
};

/** The website's own Content-Security-Policy: no third-party scripts at all. */
export const WEBSITE_CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self'",
  "img-src 'self' data: https://crafatar.com https://mc-heads.net",
  "connect-src 'self'",
  // The admin page reads a chosen video file locally (a blob: URL) to turn it into a cape.
  "media-src 'self' blob:",
  "frame-ancestors 'none'",
  "base-uri 'none'",
  "form-action 'self'",
].join("; ");

export async function serveStatic(root: string, urlPath: string): Promise<Reply> {
  let rel: string;
  try {
    rel = decodeURIComponent(urlPath);
  } catch {
    throw new HttpError(400, "bad_path", "Bad path");
  }
  if (rel.includes("\0")) throw new HttpError(400, "bad_path", "Bad path");
  if (rel.endsWith("/")) rel += "index.html";
  const base = resolve(root);
  let file = resolve(join(base, normalize(rel)));
  if (file !== base && !file.startsWith(base + sep)) throw new HttpError(404, "not_found", "Not found");

  let info = await stat(file).catch(() => null);
  if (info?.isDirectory()) {
    file = join(file, "index.html");
    info = await stat(file).catch(() => null);
  } else if (!info && !extname(file)) {
    // Pretty URLs: /pricing → pricing.html
    file = `${file}.html`;
    info = await stat(file).catch(() => null);
  }
  if (!info?.isFile()) {
    const notFound = await readFile(join(base, "404.html")).catch(() => null);
    if (notFound) return { status: 404, raw: notFound, headers: { "Content-Type": TYPES[".html"]!, "Content-Security-Policy": WEBSITE_CSP } };
    throw new HttpError(404, "not_found", "Not found");
  }
  const ext = extname(file).toLowerCase();
  const body = await readFile(file);
  const html = ext === ".html";
  return {
    status: 200,
    raw: body,
    headers: {
      "Content-Type": TYPES[ext] ?? "application/octet-stream",
      // Pages, scripts and styles are re-checked on every load, so a server update shows at once
      // (an hour-long cache once paired a new admin page with its old script). Images can wait.
      "Cache-Control": html || ext === ".js" || ext === ".css" ? "no-cache" : "public, max-age=3600",
      ...(html ? { "Content-Security-Policy": WEBSITE_CSP } : {}),
    },
  };
}
