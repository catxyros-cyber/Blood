/**
 * A very small runtime validator for request bodies and API responses.
 * Dependency-free so the launcher bundle stays small.
 */

export type Result<T> = { ok: true; value: T } | { ok: false; error: string };

export interface Schema<T> {
  parse(input: unknown, path?: string): Result<T>;
}

export type Infer<S> = S extends Schema<infer T> ? T : never;

const ok = <T>(value: T): Result<T> => ({ ok: true, value });
const fail = (path: string, msg: string): Result<never> => ({ ok: false, error: `${path || "value"} ${msg}` });

export const v = {
  string(opts: { min?: number; max?: number; pattern?: RegExp; trim?: boolean } = {}): Schema<string> {
    return {
      parse(input, path = "") {
        if (typeof input !== "string") return fail(path, "must be a string");
        const s = opts.trim ? input.trim() : input;
        if (opts.min !== undefined && s.length < opts.min) return fail(path, `must be at least ${opts.min} characters`);
        if (opts.max !== undefined && s.length > opts.max) return fail(path, `must be at most ${opts.max} characters`);
        if (opts.pattern && !opts.pattern.test(s)) return fail(path, "has an invalid format");
        return ok(s);
      },
    };
  },
  number(opts: { min?: number; max?: number; int?: boolean } = {}): Schema<number> {
    return {
      parse(input, path = "") {
        if (typeof input !== "number" || !Number.isFinite(input)) return fail(path, "must be a number");
        if (opts.int && !Number.isInteger(input)) return fail(path, "must be an integer");
        if (opts.min !== undefined && input < opts.min) return fail(path, `must be ≥ ${opts.min}`);
        if (opts.max !== undefined && input > opts.max) return fail(path, `must be ≤ ${opts.max}`);
        return ok(input);
      },
    };
  },
  boolean(): Schema<boolean> {
    return { parse: (input, path = "") => (typeof input === "boolean" ? ok(input) : fail(path, "must be true or false")) };
  },
  literal<T extends string>(...values: T[]): Schema<T> {
    return {
      parse: (input, path = "") =>
        typeof input === "string" && (values as string[]).includes(input) ? ok(input as T) : fail(path, `must be one of ${values.join(", ")}`),
    };
  },
  optional<T>(inner: Schema<T>): Schema<T | undefined> {
    return { parse: (input, path) => (input === undefined || input === null ? ok(undefined) : inner.parse(input, path)) };
  },
  array<T>(inner: Schema<T>, opts: { max?: number } = {}): Schema<T[]> {
    return {
      parse(input, path = "") {
        if (!Array.isArray(input)) return fail(path, "must be a list");
        if (opts.max !== undefined && input.length > opts.max) return fail(path, `may have at most ${opts.max} items`);
        const out: T[] = [];
        for (let i = 0; i < input.length; i++) {
          const r = inner.parse(input[i], `${path}[${i}]`);
          if (!r.ok) return r;
          out.push(r.value);
        }
        return ok(out);
      },
    };
  },
  /** Any JSON object (checked for being a plain object only). */
  record(): Schema<Record<string, unknown>> {
    return {
      parse: (input, path = "") =>
        input && typeof input === "object" && !Array.isArray(input) ? ok(input as Record<string, unknown>) : fail(path, "must be an object"),
    };
  },
  object<S extends Record<string, Schema<unknown>>>(shape: S): Schema<{ [K in keyof S]: Infer<S[K]> }> {
    return {
      parse(input, path = "") {
        if (!input || typeof input !== "object" || Array.isArray(input)) return fail(path, "must be an object");
        const out: Record<string, unknown> = {};
        for (const [key, schema] of Object.entries(shape)) {
          const r = schema.parse((input as Record<string, unknown>)[key], path ? `${path}.${key}` : key);
          if (!r.ok) return r;
          if (r.value !== undefined) out[key] = r.value;
        }
        return ok(out as { [K in keyof S]: Infer<S[K]> });
      },
    };
  },
};
