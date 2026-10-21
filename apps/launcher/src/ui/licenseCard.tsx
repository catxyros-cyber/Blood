import { useEffect, useState } from "react";
import { normalizeKey } from "@bloom/shared";
import { app, errorMessage } from "../services/app";
import { checkLicense, moveKeyHere, redeemKey, removeKey } from "../services/license";
import { useStore } from "../services/store";
import { Button, Card, confirm, cx, Spinner, TextInput } from "./components";
import { Icon } from "./icons";

/** "in 3 h 20 min" style countdown, re-rendered every 30 s. */
function useCountdown(until: number | null | undefined): string | null {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!until) return;
    const t = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(t);
  }, [until]);
  if (!until || until <= now) return null;
  const mins = Math.ceil((until - now) / 60_000);
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return h ? `${h} h ${m} min` : `${m} min`;
}

const fmtDate = (ms: number) => new Date(ms).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });

/**
 * Redeem a Premium key, see its status, and move it to this PC.
 * Shown on the dashboard and the Premium page.
 */
export function LicenseCard({ compact = false }: { compact?: boolean }) {
  const configured = useStore(app, (s) => s.premium.configured);
  const lic = useStore(app, (s) => s.premium.license);
  const purchased = useStore(app, (s) => s.premium.entitlement?.premium === true && s.premium.entitlement.source !== "license");
  const [key, setKey] = useState("");
  const [busy, setBusy] = useState<"redeem" | "move" | "check" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const wait = useCountdown(lic?.nextMoveAt);

  const status = lic?.status ?? "none";
  // On the dashboard, someone who bought Premium with their account doesn't need this card.
  if (compact && purchased && status === "none") return null;

  const run = async (kind: "redeem" | "move" | "check", fn: () => Promise<unknown>) => {
    setBusy(kind);
    setError(null);
    try {
      await fn();
      if (kind === "redeem") setKey("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const valid = normalizeKey(key) !== null;
  const showEntry = status === "none" || status === "expired" || status === "revoked";

  return (
    <Card className={cx("p-5", status === "active" && "border-amber-300/25 bg-gradient-to-br from-amber-300/[0.05] to-panel")}>
      <div className="flex items-center gap-2">
        <Icon.Key size={17} className={status === "active" ? "text-amber-200" : "text-accent"} />
        <h3 className="font-semibold text-[14.5px] flex-1">Premium key</h3>
        {status === "checking" && <Spinner size={14} />}
        {status === "active" && (
          <span className="inline-flex items-center gap-1 h-[20px] px-1.5 rounded-md text-[11px] font-semibold border border-amber-300/30 bg-amber-300/10 text-amber-200">
            <Icon.Crown size={12} /> Active
          </span>
        )}
      </div>

      {status === "active" && lic && (
        <div className="mt-3 space-y-1 text-[13px]">
          <div className="flex justify-between">
            <span className="text-muted">Key</span>
            <span className="font-mono">…{lic.hint}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted">Premium</span>
            <span>{lic.expiresAt ? `until ${fmtDate(lic.expiresAt)}` : "Lifetime"}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted">Tied to</span>
            <span>This PC</span>
          </div>
          {lic.cached && <div className="text-[12px] text-warn pt-1">Offline · showing the last check.</div>}
          {!compact && (
            <p className="text-[12px] text-faint pt-2 leading-relaxed">
              Got a new PC? Redeem the same key there and choose <strong className="text-muted">Move to this PC</strong>. A key can change PC once every 24 hours.
            </p>
          )}
        </div>
      )}

      {status === "elsewhere" && lic && (
        <div className="mt-3">
          <p className="text-[13px] text-muted leading-relaxed">
            Key <span className="font-mono text-fg">…{lic.hint}</span> is active on another PC. A key works on one PC at a time.
          </p>
          <Button className="mt-3 w-full" variant="primary" disabled={!!wait || busy !== null} loading={busy === "move"} onClick={() => void run("move", moveKeyHere)}>
            {wait ? `Move available in ${wait}` : "Move to this PC"}
          </Button>
          <p className="text-[12px] text-faint mt-2 leading-relaxed">Moving turns Premium off on the other PC. You can move a key once every 24 hours.</p>
        </div>
      )}

      {status === "error" && (
        <div className="mt-3">
          <p className="text-[13px] text-warn">Couldn't check your key{lic?.hint ? ` (…${lic.hint})` : ""}.</p>
          {lic?.error && <p className="text-[12px] text-faint mt-1 selectable">{lic.error}</p>}
          <Button size="sm" className="mt-3" loading={busy === "check"} onClick={() => void run("check", checkLicense)}>
            Try again
          </Button>
        </div>
      )}

      {showEntry && (
        <div className="mt-3">
          {status === "expired" && <p className="text-[13px] text-warn mb-2">Your key{lic?.hint ? ` …${lic.hint}` : ""} has expired.</p>}
          {status === "revoked" && <p className="text-[13px] text-danger mb-2">Your key{lic?.hint ? ` …${lic.hint}` : ""} has been disabled.</p>}
          {status === "none" && <p className="text-[13px] text-muted mb-3 leading-relaxed">Have a key? Redeem it to unlock Premium on this PC.</p>}
          <TextInput value={key} onChange={setKey} placeholder="BLOOD-XXXXX-XXXXX-XXXXX-XXXXX" mono onEnter={() => valid && configured && void run("redeem", () => redeemKey(key))} />
          <Button className="mt-3 w-full" variant="primary" disabled={!valid || !configured || busy !== null} loading={busy === "redeem"} onClick={() => void run("redeem", () => redeemKey(key))}>
            Redeem key
          </Button>
          {!configured && <p className="text-[12px] text-faint mt-2">This build isn't connected to BLOOD's server, so keys can't be checked.</p>}
        </div>
      )}

      {error && <p className="text-[12.5px] text-danger mt-3 selectable">{error}</p>}

      {!compact && status !== "none" && status !== "checking" && (
        <button
          type="button"
          className="text-[12px] text-faint hover:text-muted cursor-pointer mt-4"
          onClick={async () => {
            const ok = await confirm({
              title: "Remove the key from this PC?",
              message: "Premium from this key turns off here. The key stays tied to this PC, so you can redeem it here again any time.",
              confirmLabel: "Remove",
              danger: true,
            });
            if (ok) await removeKey();
          }}
        >
          Remove key from this PC
        </button>
      )}
    </Card>
  );
}
