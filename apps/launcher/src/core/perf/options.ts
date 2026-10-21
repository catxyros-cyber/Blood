/**
 * Read and write Minecraft's options.txt without disturbing anything BLOOD
 * doesn't change: key order, unknown keys and keybinds are preserved.
 */

export class OptionsFile {
  private lines: { key?: string; value?: string; raw: string }[] = [];

  constructor(text = "") {
    for (const raw of text.replace(/\r\n/g, "\n").split("\n")) {
      if (raw === "") continue;
      const idx = raw.indexOf(":");
      if (idx <= 0) this.lines.push({ raw });
      else this.lines.push({ key: raw.slice(0, idx), value: raw.slice(idx + 1), raw });
    }
  }

  get(key: string): string | undefined {
    return this.lines.find((l) => l.key === key)?.value;
  }

  has(key: string): boolean {
    return this.lines.some((l) => l.key === key);
  }

  set(key: string, value: string): void {
    const line = this.lines.find((l) => l.key === key);
    if (line) {
      line.value = value;
      line.raw = `${key}:${value}`;
    } else {
      this.lines.push({ key, value, raw: `${key}:${value}` });
    }
  }

  toString(): string {
    return `${this.lines.map((l) => l.raw).join("\n")}\n`;
  }

  entries(): [string, string][] {
    return this.lines.filter((l) => l.key !== undefined).map((l) => [l.key!, l.value ?? ""]);
  }
}
