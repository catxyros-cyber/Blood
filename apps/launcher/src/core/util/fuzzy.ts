/**
 * Small fuzzy matcher for the command palette: every query character must appear in order.
 * Higher scores for matches at word starts and for consecutive runs. Returns null for no match.
 */
export function fuzzyScore(query: string, text: string): number | null {
  const q = query.trim().toLowerCase();
  if (!q) return 0;
  const t = text.toLowerCase();
  // Exact substring beats any scattered match.
  const sub = t.indexOf(q);
  if (sub >= 0) return 1000 - sub + (sub === 0 || /[\s\-_./·]/.test(t[sub - 1] ?? "") ? 200 : 0);

  let score = 0;
  let ti = 0;
  let run = 0;
  for (const ch of q) {
    if (ch === " ") continue;
    const at = t.indexOf(ch, ti);
    if (at < 0) return null;
    const wordStart = at === 0 || /[\s\-_./·]/.test(t[at - 1] ?? "");
    run = at === ti ? run + 1 : 0;
    score += 10 + (wordStart ? 15 : 0) + run * 5 - Math.min(at - ti, 10);
    ti = at + 1;
  }
  return score;
}

/** Items that match, best first; ties keep their original order. */
export function fuzzyFilter<T>(query: string, items: readonly T[], text: (item: T) => string): T[] {
  if (!query.trim()) return [...items];
  return items
    .map((item, i) => ({ item, i, s: fuzzyScore(query, text(item)) }))
    .filter((x): x is { item: T; i: number; s: number } => x.s !== null)
    .sort((a, b) => b.s - a.s || a.i - b.i)
    .map((x) => x.item);
}
