import { useEffect, useLayoutEffect, useRef, useState, type ButtonHTMLAttributes, type KeyboardEvent as ReactKeyboardEvent, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { createStore, useStore } from "../services/store";
import { Icon } from "./icons";

export function cx(...c: (string | false | null | undefined)[]): string {
  return c.filter(Boolean).join(" ");
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

type Variant = "primary" | "secondary" | "ghost" | "danger" | "outline";
const VARIANTS: Record<Variant, string> = {
  primary: "bg-accent text-accent-ink hover:brightness-107 active:brightness-95 font-semibold",
  secondary: "bg-raised text-fg hover:bg-line-strong border border-line font-medium",
  ghost: "text-muted hover:text-fg hover:bg-raised font-medium",
  danger: "bg-danger/12 text-danger hover:bg-danger/20 border border-danger/25 font-medium",
  outline: "border border-line-strong text-fg hover:bg-raised font-medium",
};

export function Button({
  variant = "secondary",
  size = "md",
  icon,
  loading,
  className,
  children,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: "sm" | "md" | "lg"; icon?: ReactNode; loading?: boolean }) {
  const sizes = { sm: "h-8 px-3 text-[13px] gap-1.5 rounded-lg", md: "h-9 px-3.5 text-[13px] gap-2 rounded-lg", lg: "h-11 px-5 text-[14px] gap-2 rounded-lg" };
  return (
    <button
      type="button"
      className={cx(
        "inline-flex items-center justify-center whitespace-nowrap transition-colors duration-150 disabled:opacity-45 disabled:pointer-events-none cursor-pointer",
        sizes[size],
        VARIANTS[variant],
        className,
      )}
      disabled={loading || rest.disabled}
      {...rest}
    >
      {loading ? <Spinner size={14} /> : icon}
      {children}
    </button>
  );
}

export function IconButton({ label, className, children, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={cx("grid place-items-center h-8 w-8 rounded-lg text-muted hover:text-fg hover:bg-raised transition-colors cursor-pointer disabled:opacity-40", className)}
      {...rest}
    >
      {children}
    </button>
  );
}

// ---------------------------------------------------------------------------
// Layout primitives
// ---------------------------------------------------------------------------

export function Card({ className, children, onClick }: { className?: string; children: ReactNode; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      className={cx("rounded-xl border border-line bg-panel", onClick && "cursor-pointer hover:border-line-strong transition-colors", className)}
    >
      {children}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex items-end justify-between gap-6 mb-7">
      <div className="min-w-0">
        <h1 className="font-display text-[24px] font-semibold tracking-tight leading-tight truncate">{title}</h1>
        {subtitle && <p className="text-muted text-[14px] mt-1.5 max-w-2xl leading-relaxed">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
    </div>
  );
}

export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-center justify-between mb-3">
      <h2 className="text-[14px] font-semibold text-fg">{children}</h2>
      {action}
    </div>
  );
}

export function Badge({ tone = "neutral", children, className }: { tone?: "neutral" | "accent" | "warn" | "danger" | "info"; children: ReactNode; className?: string }) {
  const tones = {
    neutral: "bg-raised text-muted border-line",
    accent: "bg-accent/12 text-accent border-accent/25",
    warn: "bg-warn/10 text-warn border-warn/25",
    danger: "bg-danger/10 text-danger border-danger/25",
    info: "bg-info/10 text-info border-info/25",
  };
  return <span className={cx("inline-flex items-center gap-1 h-[22px] px-2 rounded-md border text-[12px] font-medium", tones[tone], className)}>{children}</span>;
}

export function Spinner({ size = 16 }: { size?: number }) {
  return (
    <span
      className="inline-block rounded-full border-2 border-current border-t-transparent"
      style={{ width: size, height: size, animation: "bloom-spin 0.7s linear infinite" }}
      aria-label="Loading"
    />
  );
}

export function ProgressBar({ value, className }: { value: number | null; className?: string }) {
  return (
    <div className={cx("h-1.5 rounded-full bg-line overflow-hidden", className)}>
      {value === null ? (
        <div className="h-full w-2/5 rounded-full bg-accent" style={{ animation: "bloom-indeterminate 1.1s ease-in-out infinite" }} />
      ) : (
        <div className="h-full rounded-full bg-accent transition-[width] duration-300" style={{ width: `${Math.max(2, value * 100)}%` }} />
      )}
    </div>
  );
}

export function EmptyState({ icon, title, children, action }: { icon?: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center text-center py-16 px-6 animate-in">
      <div className="h-12 w-12 rounded-xl bg-raised border border-line grid place-items-center text-muted mb-4">{icon ?? <Icon.Sparkle size={22} />}</div>
      <h3 className="font-display text-[16px] font-semibold">{title}</h3>
      {children && <p className="text-muted text-[14px] mt-1.5 max-w-md leading-relaxed">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cx("skeleton rounded-lg", className)} />;
}

export function Callout({ tone = "info", title, children, action }: { tone?: "info" | "warn" | "danger" | "accent"; title?: string; children: ReactNode; action?: ReactNode }) {
  const tones = {
    info: "border-info/25 bg-info/5 text-info",
    warn: "border-warn/25 bg-warn/5 text-warn",
    danger: "border-danger/30 bg-danger/5 text-danger",
    accent: "border-accent/25 bg-accent/5 text-accent",
  };
  const icon = tone === "danger" || tone === "warn" ? <Icon.Alert size={17} /> : <Icon.Info size={17} />;
  return (
    <div className={cx("flex gap-3 rounded-lg border px-4 py-3", tones[tone])}>
      <div className="mt-0.5 shrink-0">{icon}</div>
      <div className="flex-1 min-w-0 text-fg">
        {title && <div className="font-semibold mb-0.5 break-words">{title}</div>}
        <div className="text-muted text-[14px] leading-relaxed break-words">{children}</div>
      </div>
      {action && <div className="shrink-0 self-center">{action}</div>}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Form controls
// ---------------------------------------------------------------------------

export function Field({ label, hint, children }: { label: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="block">
      <div className="text-[13px] font-medium mb-1.5">{label}</div>
      {children}
      {hint && <div className="text-[13px] text-faint mt-1.5 leading-relaxed">{hint}</div>}
    </label>
  );
}

export function TextInput({ value, onChange, placeholder, className, icon, autoFocus, onEnter, mono }: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  className?: string;
  icon?: ReactNode;
  autoFocus?: boolean;
  onEnter?: () => void;
  mono?: boolean;
}) {
  return (
    <div className={cx("flex items-center gap-2 h-9 px-3 rounded-lg bg-panel-2 border border-line focus-within:border-line-strong transition-colors", className)}>
      {icon && <span className="text-faint">{icon}</span>}
      <input
        autoFocus={autoFocus}
        value={value}
        placeholder={placeholder}
        spellCheck={false}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && onEnter?.()}
        className={cx("flex-1 min-w-0 bg-transparent outline-none placeholder:text-faint", mono && "font-mono text-[13px]")}
      />
    </div>
  );
}

export function Select<T extends string>({ value, onChange, options, className }: {
  value: T;
  onChange: (v: T) => void;
  options: { value: T; label: string; disabled?: boolean }[];
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [pos, setPos] = useState<{ left: number; width: number; top?: number; bottom?: number; maxHeight: number } | null>(null);
  const button = useRef<HTMLButtonElement>(null);
  const list = useRef<HTMLDivElement>(null);
  const typed = useRef({ text: "", at: 0 });
  const selected = options.findIndex((o) => o.value === value);
  const current = options[selected];

  const place = () => {
    const r = button.current?.getBoundingClientRect();
    if (!r) return;
    const below = window.innerHeight - r.bottom - 8;
    const above = r.top - 8;
    const want = Math.min(300, options.length * 32 + 8);
    const down = below >= want || below >= above;
    setPos({ left: r.left, width: r.width, ...(down ? { top: r.bottom + 4 } : { bottom: window.innerHeight - r.top + 4 }), maxHeight: Math.max(120, Math.min(300, down ? below : above)) });
  };
  const show = () => {
    place();
    setActive(selected >= 0 ? selected : options.findIndex((o) => !o.disabled));
    setOpen(true);
  };
  const close = (refocus: boolean) => {
    setOpen(false);
    if (refocus) button.current?.focus({ preventScroll: true });
  };
  const choose = (i: number) => {
    const o = options[i];
    if (!o || o.disabled) return;
    close(true);
    if (o.value !== value) onChange(o.value);
  };
  const move = (from: number, dir: 1 | -1) => {
    for (let k = 1; k <= options.length; k++) {
      const i = (from + dir * k + options.length * 2) % options.length;
      if (!options[i]?.disabled) return i;
    }
    return from;
  };

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (!list.current?.contains(e.target as Node) && !button.current?.contains(e.target as Node)) setOpen(false);
    };
    // The list floats over the page; anything moving underneath it closes it.
    const onScroll = (e: Event) => {
      if (!list.current?.contains(e.target as Node)) setOpen(false);
    };
    const onResize = () => setOpen(false);
    window.addEventListener("mousedown", onDown);
    window.addEventListener("scroll", onScroll, true);
    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("mousedown", onDown);
      window.removeEventListener("scroll", onScroll, true);
      window.removeEventListener("resize", onResize);
    };
  }, [open]);
  useLayoutEffect(() => {
    if (!open) return;
    list.current?.focus({ preventScroll: true });
  }, [open]);
  useEffect(() => {
    if (open && active >= 0) list.current?.querySelector<HTMLElement>(`[data-i="${active}"]`)?.scrollIntoView({ block: "nearest" });
  }, [open, active]);

  const onListKey = (e: ReactKeyboardEvent) => {
    if (e.key === "Escape") {
      e.preventDefault();
      e.stopPropagation();
      close(true);
    } else if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => move(a < 0 ? (e.key === "ArrowDown" ? -1 : 0) : a, e.key === "ArrowDown" ? 1 : -1));
    } else if (e.key === "Home" || e.key === "End") {
      e.preventDefault();
      setActive(e.key === "Home" ? move(-1, 1) : move(options.length, -1));
    } else if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      choose(active);
    } else if (e.key === "Tab") {
      setOpen(false);
    } else if (e.key.length === 1) {
      // Type to jump, like a native list.
      const now = Date.now();
      typed.current = { text: (now - typed.current.at < 700 ? typed.current.text : "") + e.key.toLowerCase(), at: now };
      const hit = options.findIndex((o) => !o.disabled && o.label.toLowerCase().startsWith(typed.current.text));
      if (hit >= 0) setActive(hit);
    }
  };

  return (
    <div className={cx("relative", className)}>
      <button
        ref={button}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => (open ? close(false) : show())}
        onKeyDown={(e) => {
          if (!open && (e.key === "ArrowDown" || e.key === "ArrowUp" || e.key === "Enter" || e.key === " ")) {
            e.preventDefault();
            show();
          }
        }}
        className={cx(
          "w-full h-9 pl-3 pr-9 rounded-lg bg-panel-2 border text-left truncate cursor-pointer outline-none transition-colors",
          open ? "border-line-strong" : "border-line hover:border-line-strong focus-visible:border-line-strong",
        )}
      >
        {current?.label ?? ""}
      </button>
      <Icon.ChevronDown size={16} className={cx("absolute right-3 top-1/2 -translate-y-1/2 text-faint pointer-events-none transition-transform duration-150", open && "rotate-180")} />
      {open &&
        pos &&
        createPortal(
          <div
            ref={list}
            role="listbox"
            tabIndex={-1}
            aria-activedescendant={active >= 0 ? `opt-${active}` : undefined}
            onKeyDown={onListKey}
            className="fixed z-[80] overflow-y-auto rounded-lg border border-line-strong bg-raised p-1 shadow-xl shadow-black/40 animate-pop outline-none"
            style={{ left: pos.left, minWidth: pos.width, maxWidth: Math.max(pos.width, 420), top: pos.top, bottom: pos.bottom, maxHeight: pos.maxHeight }}
          >
            {options.map((o, i) => (
              <div
                key={o.value}
                id={`opt-${i}`}
                data-i={i}
                role="option"
                aria-selected={o.value === value}
                aria-disabled={o.disabled || undefined}
                onMouseMove={() => !o.disabled && active !== i && setActive(i)}
                onClick={() => choose(i)}
                className={cx(
                  "flex items-center gap-2 h-8 pl-2 pr-3 rounded-md text-[13px] whitespace-nowrap",
                  o.disabled ? "opacity-40 cursor-default" : "cursor-pointer",
                  i === active && !o.disabled && "bg-line-strong",
                )}
              >
                <span className="w-4 shrink-0 text-accent">{o.value === value && <Icon.Check size={14} />}</span>
                <span className="truncate">{o.label}</span>
              </div>
            ))}
          </div>,
          document.body,
        )}
    </div>
  );
}

export function Toggle({ checked, onChange, label, description, disabled }: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label?: ReactNode;
  description?: ReactNode;
  disabled?: boolean;
}) {
  const sw = (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cx(
        "relative h-[22px] w-[38px] shrink-0 rounded-full transition-colors cursor-pointer disabled:opacity-40",
        checked ? "bg-accent" : "bg-line-strong",
      )}
    >
      <span className={cx("absolute top-[3px] h-4 w-4 rounded-full bg-white shadow transition-all duration-200", checked ? "left-[19px]" : "left-[3px]")} />
    </button>
  );
  if (!label) return sw;
  return (
    <div className="flex items-center justify-between gap-6 py-1.5">
      <div className="min-w-0">
        <div className="font-medium">{label}</div>
        {description && <div className="text-[13px] text-muted mt-0.5">{description}</div>}
      </div>
      {sw}
    </div>
  );
}

export function Slider({ value, min, max, step, onChange, format }: {
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
  format?: (v: number) => string;
}) {
  const fill = ((value - min) / Math.max(1, max - min)) * 100;
  return (
    <div className="flex items-center gap-4">
      <input
        type="range"
        className="bloom-range flex-1"
        min={min}
        max={max}
        step={step}
        value={value}
        style={{ ["--fill" as string]: `${fill}%` }}
        onChange={(e) => onChange(Number(e.target.value))}
      />
      <span className="w-20 text-right tabular-nums font-medium">{format ? format(value) : value}</span>
    </div>
  );
}

export function Tabs<T extends string>({ value, onChange, tabs }: { value: T; onChange: (v: T) => void; tabs: { value: T; label: string; count?: number }[] }) {
  return (
    <div className="flex items-center gap-1 border-b border-line">
      {tabs.map((t) => (
        <button
          key={t.value}
          type="button"
          onClick={() => onChange(t.value)}
          className={cx(
            "relative h-10 px-3.5 text-[14px] font-medium transition-colors cursor-pointer",
            value === t.value ? "text-fg" : "text-muted hover:text-fg",
          )}
        >
          {t.label}
          {t.count !== undefined && <span className="ml-1.5 text-faint tabular-nums">{t.count}</span>}
          {value === t.value && <span className="absolute left-2 right-2 -bottom-px h-0.5 rounded-full bg-accent" />}
        </button>
      ))}
    </div>
  );
}

export function Segmented<T extends string>({ value, onChange, options }: { value: T; onChange: (v: T) => void; options: { value: T; label: string }[] }) {
  return (
    <div className="inline-flex p-0.5 rounded-lg bg-panel-2 border border-line">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          onClick={() => onChange(o.value)}
          className={cx(
            "h-7 px-3 rounded-md text-[13px] font-medium transition-colors cursor-pointer",
            value === o.value ? "bg-raised text-fg" : "text-muted hover:text-fg",
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Menus
// ---------------------------------------------------------------------------

export function Menu({ trigger, items, align = "right" }: {
  trigger: ReactNode;
  items: ({ label: string; icon?: ReactNode; danger?: boolean; onClick: () => void; disabled?: boolean } | "divider")[];
  align?: "left" | "right";
}) {
  const [open, setOpen] = useState(false);
  const [up, setUp] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const close = (refocus: boolean) => {
    setOpen(false);
    if (refocus) (ref.current?.querySelector("button, [tabindex]") as HTMLElement | null)?.focus();
  };
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    window.addEventListener("mousedown", onDown);
    return () => window.removeEventListener("mousedown", onDown);
  }, [open]);
  useLayoutEffect(() => {
    if (!open || !panel.current) return;
    // Open upwards when there isn't room below (rows near the bottom of the window).
    const h = panel.current.offsetHeight;
    const trig = ref.current?.getBoundingClientRect();
    if (trig) setUp(trig.bottom + 4 + h > window.innerHeight - 8 && trig.top > h + 8);
    // Focus the menu so arrows and Escape work; no item is highlighted until the keyboard moves.
    panel.current.focus({ preventScroll: true });
  }, [open]);
  const onKey = (e: ReactKeyboardEvent) => {
    const buttons: HTMLButtonElement[] = panel.current ? Array.from(panel.current.querySelectorAll<HTMLButtonElement>("button:not(:disabled)")) : [];
    const at = buttons.indexOf(document.activeElement as HTMLButtonElement);
    if (e.key === "Escape") {
      e.preventDefault();
      e.stopPropagation();
      close(true);
    } else if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      const next = e.key === "ArrowDown" ? (at + 1) % buttons.length : (at <= 0 ? buttons.length : at) - 1;
      buttons[next]?.focus();
    } else if (e.key === "Home" || e.key === "End") {
      e.preventDefault();
      buttons[e.key === "Home" ? 0 : buttons.length - 1]?.focus();
    } else if (e.key === "Tab") {
      setOpen(false);
    }
  };
  return (
    <div className="relative" ref={ref} onClick={(e) => e.stopPropagation()}>
      <div
        onClick={() => setOpen((o) => !o)}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown" && !open) {
            e.preventDefault();
            setOpen(true);
          }
        }}
      >
        {trigger}
      </div>
      {open && (
        <div
          ref={panel}
          role="menu"
          tabIndex={-1}
          onKeyDown={onKey}
          className={cx(
            "absolute z-40 min-w-48 rounded-lg border border-line-strong bg-raised p-1 shadow-xl shadow-black/40 animate-pop outline-none",
            align === "right" ? "right-0" : "left-0",
            up ? "bottom-full mb-1 origin-bottom" : "top-full mt-1 origin-top",
          )}
        >
          {items.map((it, i) =>
            it === "divider" ? (
              <div key={i} role="separator" className="my-1 h-px bg-line" />
            ) : (
              <button
                key={i}
                type="button"
                role="menuitem"
                disabled={it.disabled}
                onClick={() => {
                  setOpen(false);
                  it.onClick();
                }}
                className={cx(
                  "flex w-full items-center gap-2.5 h-8 px-2.5 rounded-md text-left text-[13px] cursor-pointer disabled:opacity-40 outline-none",
                  it.danger ? "text-danger hover:bg-danger/10 focus-visible:bg-danger/10" : "text-fg hover:bg-line-strong focus-visible:bg-line-strong",
                )}
              >
                <span className="text-muted">{it.icon}</span>
                {it.label}
              </button>
            ),
          )}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Modals & confirmations
// ---------------------------------------------------------------------------

export function Modal({ open, onClose, title, children, footer, width = 520 }: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  width?: number;
}) {
  const box = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    if (!open) return;
    const before = document.activeElement as HTMLElement | null;
    // Focus the first control that asked for it, else the dialog itself.
    const t = requestAnimationFrame(() => {
      const el = box.current;
      if (!el || el.contains(document.activeElement)) return;
      (el.querySelector<HTMLElement>("[autofocus], [data-autofocus]") ?? el).focus({ preventScroll: true });
    });
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") return closeRef.current();
      if (e.key !== "Tab" || !box.current) return;
      // Keep Tab inside the dialog.
      const f: HTMLElement[] = [];
      box.current
        .querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])')
        .forEach((x) => {
          if (x.offsetParent !== null) f.push(x);
        });
      if (!f.length) return;
      const first = f[0]!, last = f[f.length - 1]!;
      if (e.shiftKey && (document.activeElement === first || document.activeElement === box.current)) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => {
      cancelAnimationFrame(t);
      window.removeEventListener("keydown", onKey);
      before?.focus?.({ preventScroll: true });
    };
  }, [open]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/65 p-6" onMouseDown={onClose}>
      <div
        ref={box}
        tabIndex={-1}
        className="w-full max-h-[85vh] flex flex-col rounded-xl border border-line-strong bg-panel shadow-2xl shadow-black/50 animate-pop outline-none"
        style={{ maxWidth: width }}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <div className="flex items-center justify-between px-5 pt-4 pb-3">
          <h2 className="font-display text-[16px] font-semibold">{title}</h2>
          <IconButton label="Close" onClick={onClose}>
            <Icon.Close size={16} />
          </IconButton>
        </div>
        <div className="px-5 pb-5 overflow-y-auto">{children}</div>
        {footer && <div className="flex justify-end gap-2 px-5 py-3.5 border-t border-line">{footer}</div>}
      </div>
    </div>
  );
}

interface ConfirmRequest {
  title: string;
  message: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  resolve: (ok: boolean) => void;
}

const confirmStore = createStore<{ req: ConfirmRequest | null }>({ req: null });

/** Ask the user to confirm a destructive action. */
export function confirm(opts: Omit<ConfirmRequest, "resolve">): Promise<boolean> {
  return new Promise((resolve) => confirmStore.set({ req: { ...opts, resolve } }));
}

export function ConfirmHost() {
  const req = useStore(confirmStore, (s) => s.req);
  const done = (ok: boolean) => {
    req?.resolve(ok);
    confirmStore.set({ req: null });
  };
  return (
    <Modal
      open={Boolean(req)}
      onClose={() => done(false)}
      title={req?.title}
      width={460}
      footer={
        <>
          <Button variant="ghost" onClick={() => done(false)}>
            Cancel
          </Button>
          <Button variant={req?.danger ? "danger" : "primary"} onClick={() => done(true)} autoFocus>
            {req?.confirmLabel}
          </Button>
        </>
      }
    >
      <div className="text-muted leading-relaxed">{req?.message}</div>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1_048_576) return `${(n / 1024).toFixed(0)} KB`;
  if (n < 1_073_741_824) return `${(n / 1_048_576).toFixed(1)} MB`;
  return `${(n / 1_073_741_824).toFixed(1)} GB`;
}

export function formatCount(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(n >= 10_000 ? 0 : 1)}K`;
  return String(n);
}

export function timeAgo(ms: number | string | undefined): string {
  if (!ms) return "never";
  const t = typeof ms === "string" ? Date.parse(ms) : ms;
  const s = (Date.now() - t) / 1000;
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  if (s < 86400 * 30) return `${Math.floor(s / 86400)} d ago`;
  return new Date(t).toLocaleDateString();
}

export function gb(mb: number): string {
  return `${(mb / 1024).toFixed(mb % 1024 === 0 ? 0 : 1)} GB`;
}
