/** Tiny dependency-free SVG charts for the performance monitor. */

import { useId } from "react";

export function Sparkline({
  values,
  max,
  height = 56,
  color = "var(--bloom-accent)",
  fill = true,
  markers,
}: {
  values: number[];
  max?: number;
  height?: number;
  color?: string;
  fill?: boolean;
  /** Indices to mark (e.g. long GC pauses). */
  markers?: number[];
}) {
  const id = useId();
  const w = 300;
  if (values.length < 2) {
    return (
      <div className="grid place-items-center text-[12px] text-faint" style={{ height }}>
        Collecting data…
      </div>
    );
  }
  const top = max ?? Math.max(...values) * 1.15;
  const x = (i: number) => (i / (values.length - 1)) * w;
  const y = (v: number) => height - (Math.min(v, top) / (top || 1)) * (height - 4) - 2;
  const line = values.map((v, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ");
  return (
    <svg viewBox={`0 0 ${w} ${height}`} preserveAspectRatio="none" className="w-full" style={{ height }} aria-hidden="true">
      <defs>
        <linearGradient id={id} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor={color} stopOpacity="0.28" />
          <stop offset="1" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      {max !== undefined && <line x1="0" x2={w} y1={y(max)} y2={y(max)} stroke="var(--line-strong)" strokeDasharray="3 4" vectorEffect="non-scaling-stroke" />}
      {fill && <path d={`${line} L${w},${height} L0,${height} Z`} fill={`url(#${id})`} />}
      <path d={line} fill="none" stroke={color} strokeWidth="1.8" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
      {markers?.map((i) => (
        <circle key={i} cx={x(i)} cy={y(values[i]!)} r="3" fill="var(--color-warn, #fbbf24)" />
      ))}
    </svg>
  );
}

export function Metric({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: "warn" | "good" }) {
  return (
    <div>
      <div className="text-[13px] text-faint font-medium">{label}</div>
      <div className={`font-display text-[24px] font-semibold tabular-nums mt-0.5 ${tone === "warn" ? "text-warn" : tone === "good" ? "text-accent" : ""}`}>{value}</div>
      {sub && <div className="text-[12px] text-muted">{sub}</div>}
    </div>
  );
}
