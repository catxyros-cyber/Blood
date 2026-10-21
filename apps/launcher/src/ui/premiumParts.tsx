import type { ReactNode } from "react";
import { app, navigate } from "../services/app";
import { useStore } from "../services/store";
import { Button, cx } from "./components";
import { Icon } from "./icons";

export function usePremium(): boolean {
  return useStore(app, (s) => s.premium.entitlement?.premium === true);
}

export function PremiumBadge({ className, label = "Premium" }: { className?: string; label?: string }) {
  return (
    <span
      className={cx(
        "inline-flex items-center gap-1 h-[20px] px-1.5 rounded-md text-[11px] font-semibold border border-amber-300/30 bg-amber-300/10 text-amber-200",
        className,
      )}
    >
      <Icon.Crown size={12} />
      {label}
    </span>
  );
}

/** Shown in place of a Premium feature for Free users. */
export function PremiumLock({ title, children, compact }: { title: string; children?: ReactNode; compact?: boolean }) {
  return (
    <div className={cx("rounded-2xl border border-dashed border-amber-300/25 bg-amber-300/[0.03] flex items-center gap-4", compact ? "p-4" : "p-6")}>
      <div className="h-10 w-10 rounded-xl bg-amber-300/10 text-amber-200 grid place-items-center shrink-0">
        <Icon.Lock size={18} />
      </div>
      <div className="flex-1 min-w-0">
        <div className="font-semibold flex items-center gap-2">
          {title} <PremiumBadge />
        </div>
        {children && <div className="text-[13px] text-muted mt-0.5 leading-relaxed">{children}</div>}
      </div>
      <Button size="sm" variant="outline" onClick={() => navigate({ page: "premium" })}>
        See Premium
      </Button>
    </div>
  );
}

/** Wrap a Premium feature: renders children for Premium users, a lock otherwise. */
export function PremiumGate({ title, description, children }: { title: string; description?: ReactNode; children: ReactNode }) {
  const premium = usePremium();
  if (premium) return <>{children}</>;
  return <PremiumLock title={title}>{description}</PremiumLock>;
}

export function UpgradeCard() {
  const premium = usePremium();
  const configured = useStore(app, (s) => s.premium.configured);
  if (premium || !configured) return null;
  return (
    <div className="relative overflow-hidden rounded-2xl border border-amber-300/20 bg-gradient-to-br from-amber-300/[0.07] via-panel to-panel p-5">
      <div className="absolute -right-6 -top-6 h-28 w-28 rounded-full bg-amber-300/10 blur-2xl pointer-events-none" />
      <div className="relative">
        <div className="flex items-center gap-2 text-amber-200 font-semibold text-[13px]">
          <Icon.Crown size={15} /> BLOOD Premium
        </div>
        <div className="font-display text-[17px] font-semibold mt-2 leading-snug">Advanced presets, measured GC analytics, any accent colour and cloud sync.</div>
        <div className="text-[12.5px] text-muted mt-1.5">Launching and mod management stay free.</div>
        <Button size="sm" variant="primary" className="mt-4" onClick={() => navigate({ page: "premium" })}>
          Explore Premium
        </Button>
      </div>
    </div>
  );
}
