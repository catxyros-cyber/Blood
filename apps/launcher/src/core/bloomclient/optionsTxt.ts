/** Reading and changing MOTION's key binding in Minecraft's options.txt (one "key:value" per line). */

export const MENU_KEY_OPTION = "key_key.bloomclient.menu";

export function readOption(text: string | null, name: string): string | null {
  if (!text) return null;
  for (const line of text.split(/\r?\n/)) {
    const i = line.indexOf(":");
    if (i > 0 && line.slice(0, i) === name) return line.slice(i + 1);
  }
  return null;
}

/** Sets (or adds) one option, keeping every other line and the file's line endings. */
export function writeOption(text: string | null, name: string, value: string): string {
  if (!/^[a-z0-9._-]+$/i.test(value)) throw new Error("Invalid option value");
  const eol = text && text.includes("\r\n") ? "\r\n" : "\n";
  const lines = text ? text.split(/\r?\n/) : [];
  let found = false;
  const out = lines.map((line) => {
    const i = line.indexOf(":");
    if (i > 0 && line.slice(0, i) === name) {
      found = true;
      return `${name}:${value}`;
    }
    return line;
  });
  if (!found) {
    if (out.length && out[out.length - 1] === "") out.splice(out.length - 1, 0, `${name}:${value}`);
    else out.push(`${name}:${value}`);
  }
  return out.join(eol);
}

/** Other key bindings using the same key (by option name). */
export function conflictsFor(text: string | null, name: string, value: string): string[] {
  if (!text || value === "key.keyboard.unknown") return [];
  const out: string[] = [];
  for (const line of text.split(/\r?\n/)) {
    const i = line.indexOf(":");
    if (i <= 0) continue;
    const k = line.slice(0, i);
    if (k !== name && k.startsWith("key_") && line.slice(i + 1) === value) out.push(k.slice(4));
  }
  return out;
}
