import { useEffect, useRef, useState } from "react";
import type { DeviceCode } from "../../core/auth/microsoft";
import { activeAccount, setActiveAccount, signInWithWindow, signOut, startSignIn, type SignInFlow } from "../../services/accounts";
import { app, errorMessage, msClientId, toast } from "../../services/app";
import { native } from "../../services/native";
import { forgetAccountToken } from "../../services/premium";
import { useStore } from "../../services/store";
import { Badge, Button, Callout, Card, confirm, EmptyState, Modal, PageHeader, Spinner, timeAgo } from "../components";
import { Icon } from "../icons";
import { PremiumBadge } from "../premiumParts";
import { Avatar } from "../shell";

function signedIn(username: string) {
  toast({ kind: "success", title: `Signed in as ${username}`, message: "Ownership of Minecraft: Java Edition was verified." });
}

/** Default: Microsoft's sign-in page in a window inside BLOOD. Falls back to the code flow. */
function SignInModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [mode, setMode] = useState<"window" | "code">("window");
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (open) {
      setMode("window");
      setError(null);
    }
  }, [open]);

  useEffect(() => {
    if (!open || mode !== "window") return;
    let alive = true;
    setError(null);
    signInWithWindow()
      .then((account) => {
        if (!alive) return;
        if (account) signedIn(account.username);
        onClose();
      })
      .catch((e) => alive && setError(errorMessage(e)));
    return () => {
      alive = false;
    };
  }, [open, mode, attempt]);

  if (mode === "code") return <CodeSignInModal open={open} onClose={onClose} />;
  return (
    <Modal open={open} onClose={onClose} title="Sign in with Microsoft" width={460}>
      <div className="pb-2">
        {error ? (
          <div className="space-y-4">
            <Callout tone="danger" title="Sign-in didn't work">
              {error}
            </Callout>
            <div className="flex gap-2">
              <Button variant="primary" onClick={() => setAttempt((n) => n + 1)}>
                Try again
              </Button>
              <Button onClick={() => setMode("code")}>Sign in with a code instead</Button>
            </div>
          </div>
        ) : (
          <div className="text-center py-4">
            <div className="flex items-center justify-center gap-2 text-[14px]">
              <Spinner size={15} /> Finish signing in in the Microsoft window…
            </div>
            <p className="text-[12.5px] text-faint mt-4 leading-relaxed">
              The page is Microsoft's own, so BLOOD never sees your password. It only receives a sign-in token, stored in the Windows Credential Manager.
            </p>
            <button type="button" className="mt-5 text-[13px] text-accent hover:underline cursor-pointer" onClick={() => setMode("code")}>
              Having trouble? Sign in with a code in your browser instead
            </button>
          </div>
        )}
      </div>
    </Modal>
  );
}

function CodeSignInModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [code, setCode] = useState<DeviceCode | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const flowRef = useRef<SignInFlow | null>(null);

  useEffect(() => {
    if (!open) return;
    let alive = true;
    setCode(null);
    setError(null);
    startSignIn()
      .then((flow) => {
        if (!alive) {
          flow.cancel();
          return;
        }
        flowRef.current = flow;
        setCode(flow.code);
        void native.openUrl(flow.code.verificationUri).catch(() => undefined);
        flow.done
          .then((account) => {
            if (!alive) return;
            signedIn(account.username);
            onClose();
          })
          .catch((e) => alive && setError(errorMessage(e)));
      })
      .catch((e) => alive && setError(errorMessage(e)));
    return () => {
      alive = false;
      flowRef.current?.cancel();
      flowRef.current = null;
    };
  }, [open]);

  return (
    <Modal open={open} onClose={onClose} title="Sign in with Microsoft" width={480}>
      <div className="pb-2">
        {error ? (
          <div className="space-y-4">
            <Callout tone="danger" title="Sign-in didn't work">
              {error}
            </Callout>
            <Button
              onClick={() => {
                setError(null);
                onClose();
              }}
            >
              Close
            </Button>
          </div>
        ) : !code ? (
          <div className="py-10 grid place-items-center text-muted">
            <Spinner size={22} />
          </div>
        ) : (
          <div className="text-center">
            <p className="text-muted text-[13.5px]">Your browser opened Microsoft's sign-in page. Enter this code there:</p>
            <button
              type="button"
              className="mt-4 mx-auto flex items-center gap-3 font-mono text-[30px] font-bold tracking-[0.18em] px-6 py-3 rounded-2xl bg-panel-2 border border-line-strong hover:border-accent/50 cursor-pointer selectable"
              onClick={() =>
                void navigator.clipboard.writeText(code.userCode).then(() => {
                  setCopied(true);
                  setTimeout(() => setCopied(false), 1500);
                })
              }
              title="Copy code"
            >
              {code.userCode}
              <span className="text-muted">{copied ? <Icon.Check size={20} /> : <Icon.Copy size={20} />}</span>
            </button>
            <div className="mt-5 flex justify-center gap-2">
              <Button variant="primary" icon={<Icon.External size={15} />} onClick={() => void native.openUrl(code.verificationUri)}>
                Open {new URL(code.verificationUri).host}
              </Button>
            </div>
            <div className="mt-6 flex items-center justify-center gap-2 text-[13px] text-muted">
              <Spinner size={13} /> Waiting for you to finish signing in…
            </div>
            <p className="text-[12px] text-faint mt-5 leading-relaxed">
              You sign in on Microsoft's own website. BLOOD never sees your password; it receives a token that's stored in the Windows Credential Manager.
            </p>
          </div>
        )}
      </div>
    </Modal>
  );
}

export function AccountsPage() {
  const accounts = useStore(app, (s) => s.accounts);
  const activeId = useStore(app, (s) => s.settings.activeAccountId);
  const premium = useStore(app, (s) => s.premium);
  const [signingIn, setSigningIn] = useState(false);
  void activeId;
  const active = activeAccount();
  const configured = Boolean(msClientId());

  async function remove(id: string, username: string) {
    const ok = await confirm({
      title: `Sign out ${username}?`,
      message: "BLOOD forgets this account's sign-in on this PC. Your installations and worlds are not affected.",
      confirmLabel: "Sign out",
      danger: true,
    });
    if (!ok) return;
    await forgetAccountToken(id);
    await signOut(id);
    toast({ kind: "success", title: `Signed out ${username}` });
  }

  return (
    <div className="animate-in max-w-3xl">
      <PageHeader
        title="Accounts"
        subtitle="Minecraft: Java Edition accounts on this PC."
        actions={
          <Button variant="primary" icon={<Icon.Plus size={16} />} onClick={() => setSigningIn(true)} disabled={!configured}>
            Add Microsoft account
          </Button>
        }
      />
      {!configured && (
        <div className="mb-5">
          <Callout tone="warn" title="Microsoft sign-in isn't configured">
            This build of BLOOD has no Azure application id. Developers: set VITE_MS_CLIENT_ID when building (see docs/MICROSOFT_AUTH.md), or enter one under Settings → Advanced.
          </Callout>
        </div>
      )}
      {accounts.length === 0 ? (
        <Card>
          <EmptyState
            icon={<Icon.User size={24} />}
            title="Sign in to play"
            action={
              <Button variant="primary" onClick={() => setSigningIn(true)} disabled={!configured}>
                Sign in with Microsoft
              </Button>
            }
          >
            Use the Microsoft account that owns Minecraft: Java Edition. Game Pass accounts work after launching Java Edition once from the official launcher.
          </EmptyState>
        </Card>
      ) : (
        <div className="space-y-3">
          {accounts.map((a) => {
            const isActive = a.id === active?.id;
            const isPremium = isActive && premium.entitlement?.premium;
            return (
              <Card key={a.id} className={isActive ? "p-4 border-accent/40" : "p-4"}>
                <div className="flex items-center gap-4">
                  <Avatar account={a} size={48} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-[16px]">{a.username}</span>
                      {isActive && <Badge tone="accent">Active</Badge>}
                      {isPremium && <PremiumBadge />}
                    </div>
                    <div className="text-[12.5px] text-faint mt-0.5">
                      Verified {timeAgo(a.lastVerifiedAt)} · <span className="font-mono">{a.id.slice(0, 8)}…</span>
                    </div>
                  </div>
                  {!isActive && (
                    <Button size="sm" onClick={() => setActiveAccount(a.id)}>
                      Switch to
                    </Button>
                  )}
                  <Button size="sm" variant="ghost" icon={<Icon.Power size={14} />} onClick={() => void remove(a.id, a.username)}>
                    Sign out
                  </Button>
                </div>
              </Card>
            );
          })}
        </div>
      )}
      <div className="mt-8 grid grid-cols-2 gap-4">
        <Card className="p-5">
          <Icon.Shield size={20} className="text-accent" />
          <h3 className="font-semibold mt-2">How sign-in works</h3>
          <p className="text-[13px] text-muted mt-1 leading-relaxed">
            You sign in on Microsoft's own page in a BLOOD window (or with a code in your browser), then BLOOD goes through Xbox Live and Minecraft services, and checks that the account owns Java Edition. Tokens are kept in the Windows Credential Manager and never written to logs.
          </p>
        </Card>
        <Card className="p-5">
          <Icon.Globe size={20} className="text-accent" />
          <h3 className="font-semibold mt-2">Playing offline</h3>
          <p className="text-[13px] text-muted mt-1 leading-relaxed">
            If an account has been verified online before, you can still play singleplayer when the internet is down. Multiplayer servers need an online session.
          </p>
        </Card>
      </div>
      <SignInModal open={signingIn} onClose={() => setSigningIn(false)} />
    </div>
  );
}
