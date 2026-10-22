/**
 * MOTION tags & presence. While the launcher is open it tells MOTION's server you're online (and
 * whether a game is running) about once a minute; the Home page shows the tagged team (owner etc.)
 * with who's online right now.
 */

import type { TeamMember } from "@bloom/shared";
import { activeAccount } from "./accounts";
import { app } from "./app";
import { API_URL, apiCall, premiumRequest } from "./premium";
import { createStore } from "./store";

export const teamState = createStore<{ team: TeamMember[]; loaded: boolean }>({ team: [], loaded: false });

export async function loadTeam(): Promise<void> {
  if (!API_URL) return;
  try {
    const r = await apiCall<{ team: TeamMember[] }>("GET", "/v1/team");
    if (r.status === 200) teamState.set({ team: r.body.team, loaded: true });
  } catch {
    /* offline: keep the last list */
  }
}

async function heartbeat(): Promise<void> {
  if (!API_URL || !activeAccount()) return;
  try {
    await premiumRequest("POST", "/v1/me/online", { playing: Object.keys(app.get().running).length > 0 });
  } catch {
    /* not signed in to MOTION yet, or offline */
  }
}

let timer: ReturnType<typeof setInterval> | undefined;

export function startPresence(): void {
  if (timer || !API_URL) return;
  const beat = () => void heartbeat().then(loadTeam);
  beat();
  timer = setInterval(beat, 60_000);
  // Tell the server straight away when a game starts or stops.
  let wasPlaying = false;
  app.subscribe(() => {
    const playing = Object.keys(app.get().running).length > 0;
    if (playing !== wasPlaying) {
      wasPlaying = playing;
      beat();
    }
  });
}
