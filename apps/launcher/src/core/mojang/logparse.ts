/**
 * Minecraft's client log4j configuration (the one Mojang ships for each
 * version) prints events as XML on stdout. This turns that stream — plus any
 * plain-text lines — into structured log entries.
 */

export type LogLevel = "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR" | "FATAL";

export interface LogEntry {
  time: number;
  level: LogLevel;
  thread?: string;
  logger?: string;
  message: string;
}

const LEVELS = new Set(["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"]);

function attr(tag: string, name: string): string | undefined {
  const m = new RegExp(`${name}="([^"]*)"`).exec(tag);
  return m?.[1];
}

function cdata(block: string, tag: string): string | undefined {
  const m = new RegExp(`<log4j:${tag}><!\\[CDATA\\[([\\s\\S]*?)\\]\\]></log4j:${tag}>`).exec(block);
  return m?.[1];
}

const PLAIN = /^\[(\d{2}:\d{2}:\d{2})\] \[([^\]/]+)\/([A-Z]+)\](?: \(([^)]+)\))?: ?(.*)$/;

export class LogParser {
  private buffer: string[] | null = null;

  /** Feed one line; returns zero or more completed entries. */
  push(line: string, stream: "stdout" | "stderr" = "stdout"): LogEntry[] {
    if (this.buffer) {
      this.buffer.push(line);
      if (line.includes("</log4j:Event>")) return this.flushEvent();
      return [];
    }
    const trimmed = line.trimStart();
    if (trimmed.startsWith("<log4j:Event")) {
      this.buffer = [line];
      if (line.includes("</log4j:Event>")) return this.flushEvent();
      return [];
    }
    if (!line.trim()) return [];
    const m = PLAIN.exec(line);
    if (m) {
      const level = (m[3] ?? "INFO").toUpperCase();
      return [
        {
          time: Date.now(),
          level: (LEVELS.has(level) ? level : "INFO") as LogLevel,
          thread: m[2],
          logger: m[4],
          message: m[5] ?? "",
        },
      ];
    }
    return [{ time: Date.now(), level: stream === "stderr" ? "WARN" : "INFO", message: line }];
  }

  private flushEvent(): LogEntry[] {
    const block = (this.buffer ?? []).join("\n");
    this.buffer = null;
    const open = /<log4j:Event[^>]*>/.exec(block)?.[0] ?? "";
    const level = (attr(open, "level") ?? "INFO").toUpperCase();
    const ts = Number(attr(open, "timestamp"));
    let message = cdata(block, "Message") ?? "";
    const throwable = cdata(block, "Throwable");
    if (throwable) message += `\n${throwable}`;
    return [
      {
        time: Number.isFinite(ts) && ts > 0 ? ts : Date.now(),
        level: (LEVELS.has(level) ? level : "INFO") as LogLevel,
        thread: attr(open, "thread"),
        logger: attr(open, "logger"),
        message: message.replace(/\r?\n$/, ""),
      },
    ];
  }
}

/**
 * Milestones MOTION uses to measure real load times from the game's own log.
 * "ready" fires when the game has finished loading resources (around the title screen).
 */
export function detectMilestone(message: string): "window" | "ready" | null {
  if (/^Backend library: LWJGL/.test(message) || /^LWJGL Version/.test(message)) return "window";
  if (/Sound engine started/.test(message) || /^Created: \d+x\d+x\d+ minecraft:textures\/atlas\/blocks\.png-atlas/.test(message)) {
    return "ready";
  }
  return null;
}
