/**
 * Java heap and garbage-collection statistics from the JVM's own unified GC
 * log (`-Xlog:gc`, Java 9+). These are measured values: heap in use after each
 * collection, committed heap, and stop-the-world pause times — long pauses are
 * the most common cause of Minecraft "stutter". BLOOD does not estimate FPS.
 */

export interface GcEvent {
  /** Seconds since JVM start. */
  t: number;
  id: number;
  kind: string;
  beforeMb: number;
  afterMb: number;
  /** Committed heap size reported with the event, when present. */
  committedMb?: number;
  /** Stop-the-world pause in ms (G1/Parallel/Serial "Pause …" events only). */
  pauseMs?: number;
}

export interface GcStats {
  collector?: string;
  events: number;
  /** Heap in use after the most recent collection. */
  heapUsedMb?: number;
  heapCommittedMb?: number;
  pauses: number;
  totalPauseMs: number;
  avgPauseMs: number;
  p95PauseMs: number;
  maxPauseMs: number;
  /** Pauses long enough to be visible as a hitch at 60 FPS (> 50 ms). */
  longPauses: number;
  /** Collections per minute over the logged period. */
  perMinute: number;
  /** Approximate allocation rate between collections, MB/s. */
  allocMbPerSec?: number;
  /** Heap after GC over time, for charts (at most 120 points). */
  series: { t: number; afterMb: number; pauseMs?: number }[];
  spanSec: number;
}

const UNIT: Record<string, number> = { K: 1 / 1024, M: 1, G: 1024 };
const toMb = (n: string, u: string) => Number(n) * (UNIT[u.toUpperCase()] ?? 1);

// [12.345s][info][gc] GC(12) Pause Young (Normal) (G1 Evacuation Pause) 512M->128M(1024M) 4.123ms
const G1_LIKE = /^\[(\d+(?:\.\d+)?)s\].*?\bGC\((\d+)\)\s+(.+?)\s+(\d+(?:\.\d+)?)([KMG])->(\d+(?:\.\d+)?)([KMG])\((\d+(?:\.\d+)?)([KMG])\)\s+(\d+(?:\.\d+)?)(ms|s)\s*$/;
// [5.123s][info][gc] GC(3) Minor Collection (Allocation Rate) 2010M(25%)->1234M(15%) 0.045s   (ZGC)
const ZGC = /^\[(\d+(?:\.\d+)?)s\].*?\bGC\((\d+)\)\s+(.+?)\s+(\d+(?:\.\d+)?)([KMG])\(\d+%\)->(\d+(?:\.\d+)?)([KMG])\(\d+%\)\s+(\d+(?:\.\d+)?)(ms|s)\s*$/;
const USING = /\[gc(?:,init)?\s*\]\s+Using\s+(.+?)\s*$/;

export function parseGcLog(text: string): { events: GcEvent[]; collector?: string } {
  const events: GcEvent[] = [];
  let collector: string | undefined;
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line) continue;
    const using = USING.exec(line);
    if (using) {
      collector = using[1];
      continue;
    }
    let m = G1_LIKE.exec(line);
    if (m) {
      const kind = m[3]!.trim();
      const dur = Number(m[10]) * (m[11] === "s" ? 1000 : 1);
      events.push({
        t: Number(m[1]),
        id: Number(m[2]),
        kind,
        beforeMb: toMb(m[4]!, m[5]!),
        afterMb: toMb(m[6]!, m[7]!),
        committedMb: toMb(m[8]!, m[9]!),
        pauseMs: /^Pause\b/.test(kind) ? dur : undefined,
      });
      continue;
    }
    m = ZGC.exec(line);
    if (m) {
      events.push({
        t: Number(m[1]),
        id: Number(m[2]),
        kind: m[3]!.trim(),
        beforeMb: toMb(m[4]!, m[5]!),
        afterMb: toMb(m[6]!, m[7]!),
        // ZGC collections are concurrent; their pauses are sub-millisecond and logged separately.
      });
    }
  }
  return { events, collector };
}

function percentile(sorted: number[], p: number): number {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, idx)]!;
}

export function gcStats(text: string): GcStats {
  const { events, collector } = parseGcLog(text);
  const pauses = events.map((e) => e.pauseMs).filter((x): x is number => x !== undefined);
  const sorted = [...pauses].sort((a, b) => a - b);
  const total = pauses.reduce((a, b) => a + b, 0);
  const first = events[0];
  const last = events[events.length - 1];
  const spanSec = first && last ? Math.max(0, last.t - first.t) : 0;

  let allocated = 0;
  for (let i = 1; i < events.length; i++) {
    const grew = events[i]!.beforeMb - events[i - 1]!.afterMb;
    if (grew > 0) allocated += grew;
  }

  const step = Math.max(1, Math.ceil(events.length / 120));
  const series = events.filter((_, i) => i % step === 0 || i === events.length - 1).map((e) => ({ t: e.t, afterMb: e.afterMb, pauseMs: e.pauseMs }));

  return {
    collector,
    events: events.length,
    heapUsedMb: last?.afterMb,
    heapCommittedMb: [...events].reverse().find((e) => e.committedMb !== undefined)?.committedMb,
    pauses: pauses.length,
    totalPauseMs: total,
    avgPauseMs: pauses.length ? total / pauses.length : 0,
    p95PauseMs: percentile(sorted, 95),
    maxPauseMs: sorted[sorted.length - 1] ?? 0,
    longPauses: pauses.filter((x) => x > 50).length,
    perMinute: spanSec > 0 ? (events.length - 1) / (spanSec / 60) : 0,
    allocMbPerSec: spanSec > 0 ? allocated / spanSec : undefined,
    series,
    spanSec,
  };
}

/**
 * JVM flag that writes the GC log. Windows paths contain ':' which -Xlog uses
 * as a separator, so the file name is quoted (supported since JDK 9).
 */
export function gcLogArg(path: string, javaMajor: number): string | null {
  if (javaMajor < 9) return null;
  return `-Xlog:gc:file="${path}":uptime,level,tags:filecount=0`;
}
