/** RAM and JVM recommendations. All numbers are in MiB. */

export interface MemoryAdvice {
  recommendedMb: number;
  /** Highest value MOTION considers safe on this machine. */
  maxSafeMb: number;
  reason: string;
}

const round = (mb: number) => Math.round(mb / 512) * 512;

/**
 * Recommend a heap size. More is not better: an oversized heap causes longer
 * garbage-collection pauses and starves Windows and the GPU driver.
 */
export function recommendMemory(totalSystemMb: number, opts: { modded: boolean; modCount: number }): MemoryAdvice {
  // Leave room for Windows, the browser people keep open, and the GPU driver.
  const reserve = totalSystemMb <= 8192 ? 3072 : 4096;
  const maxSafeMb = Math.max(1024, round(Math.min(totalSystemMb - reserve, totalSystemMb * 0.6, 16384)));

  let want: number;
  let reason: string;
  if (!opts.modded) {
    want = 3072;
    reason = "Vanilla Minecraft rarely benefits from more than 2–4 GB.";
  } else if (opts.modCount < 40) {
    want = 4096;
    reason = "A light modded setup runs well with about 4 GB.";
  } else if (opts.modCount < 120) {
    want = 6144;
    reason = `${opts.modCount} mods: about 6 GB keeps garbage collection smooth.`;
  } else {
    want = 8192;
    reason = `${opts.modCount} mods is a large pack; about 8 GB is typical.`;
  }
  const recommendedMb = Math.max(1024, Math.min(round(want), maxSafeMb));
  if (recommendedMb < want) reason += ` Limited to ${(recommendedMb / 1024).toFixed(1)} GB by the RAM in this PC.`;
  return { recommendedMb, maxSafeMb, reason };
}

export interface JvmPreset {
  id: "mojang" | "g1-tuned" | "zgc";
  name: string;
  description: string;
  minJava: number;
  minHeapMb: number;
  args: string[];
}

export const JVM_PRESETS: JvmPreset[] = [
  {
    id: "mojang",
    name: "Default (Mojang)",
    description: "The garbage-collector settings the official launcher uses. Safe everywhere.",
    minJava: 8,
    minHeapMb: 0,
    args: [
      "-XX:+UnlockExperimentalVMOptions",
      "-XX:+UseG1GC",
      "-XX:G1NewSizePercent=20",
      "-XX:G1ReservePercent=20",
      "-XX:MaxGCPauseMillis=50",
      "-XX:G1HeapRegionSize=32M",
    ],
  },
  {
    id: "g1-tuned",
    name: "G1 tuned",
    description: "G1 with a few extra settings that reduce stutter in modded play.",
    minJava: 11,
    minHeapMb: 3072,
    args: [
      "-XX:+UnlockExperimentalVMOptions",
      "-XX:+UseG1GC",
      "-XX:+ParallelRefProcEnabled",
      "-XX:+DisableExplicitGC",
      "-XX:MaxGCPauseMillis=50",
      "-XX:G1NewSizePercent=20",
      "-XX:G1ReservePercent=20",
      "-XX:G1HeapRegionSize=16M",
      "-XX:G1MixedGCCountTarget=4",
      "-XX:InitiatingHeapOccupancyPercent=20",
    ],
  },
  {
    id: "zgc",
    name: "Generational ZGC",
    description: "Very short GC pauses at the cost of some CPU. Needs Java 21+ and at least 6 GB of RAM allocated.",
    minJava: 21,
    minHeapMb: 6144,
    args: ["-XX:+UseZGC", "-XX:+ZGenerational"],
  },
];

export interface JvmArgIssue {
  arg: string;
  severity: "error" | "warning";
  message: string;
}

/** Catch custom JVM arguments that would stop the game from starting or fight MOTION's own settings. */
export function validateJvmArgs(args: string[], javaMajor: number): JvmArgIssue[] {
  const issues: JvmArgIssue[] = [];
  for (const arg of args) {
    if (/^-Xm[sx]/i.test(arg)) {
      issues.push({ arg, severity: "warning", message: "Memory is set with the RAM slider; this argument will be ignored." });
    } else if (arg === "-cp" || arg === "-classpath" || arg.startsWith("-Djava.library.path")) {
      issues.push({ arg, severity: "error", message: "MOTION builds the classpath itself; remove this argument." });
    } else if (/UseConcMarkSweepGC/.test(arg) && javaMajor >= 14) {
      issues.push({ arg, severity: "error", message: `CMS was removed in Java 14; Java ${javaMajor} will refuse to start.` });
    } else if (/UseZGC/.test(arg) && javaMajor < 15) {
      issues.push({ arg, severity: "error", message: `ZGC needs Java 15 or newer (this installation uses Java ${javaMajor}).` });
    } else if (/ZGenerational/.test(arg) && javaMajor < 21) {
      issues.push({ arg, severity: "error", message: "Generational ZGC needs Java 21 or newer." });
    } else if (/UseShenandoahGC/.test(arg)) {
      issues.push({ arg, severity: "warning", message: "Shenandoah isn't included in every Java build; the game may fail to start." });
    } else if (!arg.startsWith("-")) {
      issues.push({ arg, severity: "error", message: "JVM arguments must start with '-'." });
    }
  }
  const gcs = args.filter((a) => /-XX:\+Use\w+GC/.test(a));
  if (gcs.length > 1) issues.push({ arg: gcs.join(" "), severity: "error", message: "Only one garbage collector can be selected." });
  return issues;
}

/** Split a user-typed argument string, respecting quotes. */
export function splitArgs(input: string): string[] {
  const out: string[] = [];
  let cur = "";
  let quote: string | null = null;
  let has = false;
  for (const ch of input) {
    if (quote) {
      if (ch === quote) quote = null;
      else cur += ch;
    } else if (ch === '"' || ch === "'") {
      quote = ch;
      has = true;
    } else if (/\s/.test(ch)) {
      if (cur || has) out.push(cur);
      cur = "";
      has = false;
    } else {
      cur += ch;
    }
  }
  if (cur || has) out.push(cur);
  return out.filter(Boolean);
}
