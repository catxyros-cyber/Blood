/** A small HTTP toolkit on node:http: routing, bodies with size limits, cookies and errors. */

import type { IncomingMessage, ServerResponse } from "node:http";

export class HttpError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public headers: Record<string, string> = {},
  ) {
    super(message);
  }
}

export interface Reply {
  status: number;
  body?: unknown;
  headers?: Record<string, string>;
  /** Raw body (static files); `body` is ignored when set. */
  raw?: Buffer | string;
}

export interface Ctx {
  req: IncomingMessage;
  method: string;
  path: string;
  query: URLSearchParams;
  params: Record<string, string>;
  ip: string;
  secure: boolean;
  requestId: string;
  cookies: Record<string, string>;
  /** Parsed JSON body (max 64 KiB unless the route says otherwise). */
  json(limit?: number): Promise<unknown>;
  raw(limit?: number): Promise<Buffer>;
  /** Response headers to add (e.g. Set-Cookie). */
  setHeader(name: string, value: string): void;
  responseHeaders: Record<string, string[]>;
  /** Filled by auth middleware. */
  state: Record<string, unknown>;
}

export type Handler = (ctx: Ctx) => Promise<Reply> | Reply;

interface Route {
  method: string;
  parts: string[];
  handler: Handler;
}

export class Router {
  private routes: Route[] = [];
  add(method: string, pattern: string, handler: Handler) {
    this.routes.push({ method, parts: pattern.split("/").filter(Boolean), handler });
    return this;
  }
  get(p: string, h: Handler) {
    return this.add("GET", p, h);
  }
  post(p: string, h: Handler) {
    return this.add("POST", p, h);
  }
  put(p: string, h: Handler) {
    return this.add("PUT", p, h);
  }
  delete(p: string, h: Handler) {
    return this.add("DELETE", p, h);
  }

  match(method: string, path: string): { handler: Handler; params: Record<string, string> } | "method_not_allowed" | null {
    const segs = path.split("/").filter(Boolean);
    let methodMismatch = false;
    for (const r of this.routes) {
      if (r.parts.length !== segs.length) continue;
      const params: Record<string, string> = {};
      let ok = true;
      for (let i = 0; i < segs.length; i++) {
        const p = r.parts[i]!;
        if (p.startsWith(":")) {
          try {
            params[p.slice(1)] = decodeURIComponent(segs[i]!);
          } catch {
            ok = false;
            break;
          }
        } else if (p !== segs[i]) {
          ok = false;
          break;
        }
      }
      if (!ok) continue;
      if (r.method !== method && !(method === "HEAD" && r.method === "GET")) {
        methodMismatch = true;
        continue;
      }
      return { handler: r.handler, params };
    }
    return methodMismatch ? "method_not_allowed" : null;
  }
}

export function parseCookies(header: string | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  for (const part of (header ?? "").split(";")) {
    const i = part.indexOf("=");
    if (i < 0) continue;
    const k = part.slice(0, i).trim();
    const v = part.slice(i + 1).trim();
    if (!k) continue;
    try {
      out[k] = decodeURIComponent(v);
    } catch {
      out[k] = v;
    }
  }
  return out;
}

export function serializeCookie(name: string, value: string, opts: { maxAge?: number; secure: boolean; httpOnly?: boolean; sameSite?: "Lax" | "Strict"; path?: string }): string {
  const parts = [`${name}=${encodeURIComponent(value)}`, `Path=${opts.path ?? "/"}`, `SameSite=${opts.sameSite ?? "Lax"}`];
  if (opts.maxAge !== undefined) parts.push(`Max-Age=${Math.floor(opts.maxAge)}`);
  if (opts.httpOnly !== false) parts.push("HttpOnly");
  if (opts.secure) parts.push("Secure");
  return parts.join("; ");
}

export function readBody(req: IncomingMessage, limit: number): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const declared = Number(req.headers["content-length"] ?? 0);
    if (declared > limit) {
      reject(new HttpError(413, "payload_too_large", "Request body is too large"));
      req.resume();
      return;
    }
    const chunks: Buffer[] = [];
    let size = 0;
    req.on("data", (c: Buffer) => {
      size += c.length;
      if (size > limit) {
        reject(new HttpError(413, "payload_too_large", "Request body is too large"));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on("end", () => resolve(Buffer.concat(chunks)));
    req.on("error", reject);
  });
}

/** Client IP, honouring X-Forwarded-For only behind a trusted proxy. */
export function clientIp(req: IncomingMessage, trustProxy: boolean): string {
  if (trustProxy) {
    const xff = req.headers["x-forwarded-for"];
    const first = (Array.isArray(xff) ? xff[0] : xff)?.split(",")[0]?.trim();
    if (first) return first;
  }
  return req.socket.remoteAddress ?? "unknown";
}

export function isSecure(req: IncomingMessage, trustProxy: boolean): boolean {
  if (trustProxy && req.headers["x-forwarded-proto"] === "https") return true;
  return Boolean((req.socket as { encrypted?: boolean }).encrypted);
}

export function makeCtx(req: IncomingMessage, opts: { trustProxy: boolean; requestId: string }): Ctx {
  const url = new URL(req.url ?? "/", "http://localhost");
  let rawCache: Promise<Buffer> | null = null;
  const responseHeaders: Record<string, string[]> = {};
  const ctx: Ctx = {
    req,
    method: (req.method ?? "GET").toUpperCase(),
    path: url.pathname,
    query: url.searchParams,
    params: {},
    ip: clientIp(req, opts.trustProxy),
    secure: isSecure(req, opts.trustProxy),
    requestId: opts.requestId,
    cookies: parseCookies(req.headers.cookie),
    raw(limit = 64 * 1024) {
      rawCache ??= readBody(req, limit);
      return rawCache;
    },
    async json(limit = 64 * 1024) {
      const ct = String(req.headers["content-type"] ?? "");
      if (!ct.startsWith("application/json")) throw new HttpError(415, "unsupported_media_type", "Send a JSON body (Content-Type: application/json)");
      const buf = await ctx.raw(limit);
      if (buf.length === 0) return {};
      try {
        return JSON.parse(buf.toString("utf8"));
      } catch {
        throw new HttpError(400, "invalid_json", "The request body is not valid JSON");
      }
    },
    setHeader(name, value) {
      (responseHeaders[name] ??= []).push(value);
    },
    responseHeaders,
    state: {},
  };
  return ctx;
}

export function writeReply(res: ServerResponse, reply: Reply, extra: Record<string, string | string[]>, head: boolean) {
  const headers: Record<string, string | string[]> = { ...extra, ...(reply.headers ?? {}) };
  let payload: Buffer | string | undefined;
  if (reply.raw !== undefined) payload = reply.raw;
  else if (reply.body !== undefined) {
    payload = JSON.stringify(reply.body);
    headers["Content-Type"] ??= "application/json; charset=utf-8";
  }
  if (payload !== undefined) headers["Content-Length"] = String(Buffer.byteLength(payload));
  res.writeHead(reply.status, headers);
  res.end(head ? undefined : payload);
}

// ---------------------------------------------------------------------------
// Rate limiting (token bucket per key). In-memory: use one instance, or put a
// shared limiter (e.g. at the reverse proxy) in front of several instances.
// ---------------------------------------------------------------------------

export class RateLimiter {
  private buckets = new Map<string, { tokens: number; at: number }>();
  constructor(
    private capacity: number,
    private refillPerSec: number,
  ) {}
  take(key: string, now = Date.now()): { ok: boolean; retryAfterSec: number } {
    const b = this.buckets.get(key) ?? { tokens: this.capacity, at: now };
    b.tokens = Math.min(this.capacity, b.tokens + ((now - b.at) / 1000) * this.refillPerSec);
    b.at = now;
    if (b.tokens >= 1) {
      b.tokens -= 1;
      this.buckets.set(key, b);
      return { ok: true, retryAfterSec: 0 };
    }
    this.buckets.set(key, b);
    return { ok: false, retryAfterSec: Math.ceil((1 - b.tokens) / this.refillPerSec) };
  }
  sweep(now = Date.now()) {
    for (const [k, b] of this.buckets) if (now - b.at > 10 * 60_000) this.buckets.delete(k);
  }
}
