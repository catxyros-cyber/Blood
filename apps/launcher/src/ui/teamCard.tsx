import { useEffect } from "react";
import { loadTeam, teamState } from "../services/team";
import { useStore } from "../services/store";
import { Card } from "./components";
import { Icon } from "./icons";

/** "BLOOD team": tagged players (owner, staff…) and whether they're online right now. */
export function TeamCard() {
  const { team, loaded } = useStore(teamState, (s) => s);
  useEffect(() => {
    if (!loaded) void loadTeam();
  }, [loaded]);
  if (!team.length) return null;
  return (
    <Card className="p-5">
      <div className="flex items-center gap-2 mb-3">
        <Icon.Crown size={16} className="text-accent" />
        <h3 className="font-semibold">BLOOD team</h3>
      </div>
      <div className="flex flex-col gap-2">
        {team.map((m) => (
          <div key={m.username} className="flex items-center gap-2 text-[13px]">
            <span className="font-bold" style={{ color: m.color }}>[{m.text}]</span>
            <span className="flex-1 truncate">{m.username}</span>
            <span className="flex items-center gap-1.5" style={{ color: m.online ? "#3ecf6e" : undefined }}>
              <span className="w-2 h-2 rounded-full" style={{ background: m.online ? "#3ecf6e" : "rgba(255,255,255,.2)" }} />
              <span className={m.online ? "" : "text-faint"}>{m.playing ? "Playing" : m.online ? "Online" : "Offline"}</span>
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}
