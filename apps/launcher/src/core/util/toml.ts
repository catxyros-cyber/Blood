/**
 * A small TOML reader covering what Forge/NeoForge `mods.toml` files use:
 * tables, arrays of tables, strings (basic, literal, multi-line), booleans,
 * numbers, and simple inline arrays. It is not a general TOML implementation.
 */

type TomlValue = string | number | boolean | TomlValue[] | TomlTable;
export interface TomlTable {
  [key: string]: TomlValue;
}

function stripComment(line: string): string {
  let inStr: string | null = null;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]!;
    if (inStr) {
      if (ch === "\\" && inStr === '"') i++;
      else if (ch === inStr) inStr = null;
    } else if (ch === '"' || ch === "'") inStr = ch;
    else if (ch === "#") return line.slice(0, i);
  }
  return line;
}

function parseScalar(raw: string): TomlValue {
  const v = raw.trim();
  if (v.startsWith('"') && v.endsWith('"') && v.length >= 2) {
    return v
      .slice(1, -1)
      .replace(/\\n/g, "\n")
      .replace(/\\t/g, "\t")
      .replace(/\\"/g, '"')
      .replace(/\\\\/g, "\\");
  }
  if (v.startsWith("'") && v.endsWith("'") && v.length >= 2) return v.slice(1, -1);
  if (v === "true") return true;
  if (v === "false") return false;
  if (v.startsWith("[") && v.endsWith("]")) {
    const inner = v.slice(1, -1).trim();
    if (!inner) return [];
    const items: string[] = [];
    let depth = 0;
    let cur = "";
    let inStr: string | null = null;
    for (const ch of inner) {
      if (inStr) {
        cur += ch;
        if (ch === inStr) inStr = null;
        continue;
      }
      if (ch === '"' || ch === "'") inStr = ch;
      if (ch === "[" || ch === "{") depth++;
      if (ch === "]" || ch === "}") depth--;
      if (ch === "," && depth === 0) {
        items.push(cur);
        cur = "";
      } else cur += ch;
    }
    if (cur.trim()) items.push(cur);
    return items.map(parseScalar);
  }
  const n = Number(v);
  if (v !== "" && Number.isFinite(n)) return n;
  return v;
}

function getTable(root: TomlTable, path: string[], arrayLast: boolean): TomlTable {
  let cur: TomlTable = root;
  path.forEach((key, idx) => {
    const last = idx === path.length - 1;
    if (last && arrayLast) {
      const arr = (Array.isArray(cur[key]) ? cur[key] : (cur[key] = [])) as TomlValue[];
      const t: TomlTable = {};
      arr.push(t);
      cur = t;
      return;
    }
    let next = cur[key];
    if (Array.isArray(next)) next = next[next.length - 1] as TomlTable;
    if (!next || typeof next !== "object") {
      next = {};
      cur[key] = next;
    }
    cur = next as TomlTable;
  });
  return cur;
}

function splitKey(k: string): string[] {
  return k
    .split(".")
    .map((s) => s.trim().replace(/^["']|["']$/g, ""))
    .filter(Boolean);
}

export function parseToml(text: string): TomlTable {
  const root: TomlTable = {};
  let table = root;
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  for (let i = 0; i < lines.length; i++) {
    const line = stripComment(lines[i]!).trim();
    if (!line) continue;
    const arr = /^\[\[(.+)\]\]$/.exec(line);
    if (arr) {
      table = getTable(root, splitKey(arr[1]!), true);
      continue;
    }
    const tab = /^\[(.+)\]$/.exec(line);
    if (tab) {
      table = getTable(root, splitKey(tab[1]!), false);
      continue;
    }
    const eq = line.indexOf("=");
    if (eq < 0) continue;
    const keyPath = splitKey(line.slice(0, eq));
    let rawValue = line.slice(eq + 1).trim();
    // Multi-line strings
    for (const q of ['"""', "'''"]) {
      if (rawValue.startsWith(q)) {
        let body = rawValue.slice(3);
        while (!body.includes(q) && i + 1 < lines.length) body += `\n${lines[++i]}`;
        rawValue = "";
        const end = body.indexOf(q);
        const value = body.slice(0, end).replace(/^\n/, "");
        setKey(table, keyPath, value);
      }
    }
    if (!rawValue) continue;
    // Multi-line inline arrays
    if (rawValue.startsWith("[") && !rawValue.endsWith("]")) {
      while (i + 1 < lines.length && !rawValue.trimEnd().endsWith("]")) rawValue += ` ${stripComment(lines[++i]!).trim()}`;
    }
    setKey(table, keyPath, parseScalar(rawValue));
  }
  return root;
}

function setKey(table: TomlTable, path: string[], value: TomlValue) {
  const parent = path.length > 1 ? getTable(table, path.slice(0, -1), false) : table;
  parent[path[path.length - 1]!] = value;
}
