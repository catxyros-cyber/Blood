/** Minecraft: Java Edition patch notes from Mojang's launcher content feed. */

import { getJson } from "../core/platform";
import { P } from "./app";

const BASE = "https://launchercontent.mojang.com";

export interface PatchNote {
  id: string;
  title: string;
  version: string;
  type: "release" | "snapshot" | string;
  date?: string;
  shortText?: string;
  image?: string;
  contentPath?: string;
}

interface Feed {
  entries: {
    id: string;
    title: string;
    version: string;
    type: string;
    date?: string;
    shortText?: string;
    image?: { url: string; title?: string };
    contentPath?: string;
  }[];
}

/** Feed image paths are relative to the content host; absolute URLs (any scheme) are kept. */
const abs = (u?: string) => (!u ? undefined : /^[a-z][a-z0-9+.-]*:/i.test(u) ? u : `${BASE}${u.startsWith("/") ? "" : "/"}${u}`);

export async function patchNotes(limit = 6, includeSnapshots = false): Promise<PatchNote[]> {
  const feed = await getJson<Feed>(P(), `${BASE}/v2/javaPatchNotes.json`);
  return (feed.entries ?? [])
    .filter((e) => includeSnapshots || e.type === "release")
    .slice(0, limit)
    .map((e) => ({
      id: e.id,
      title: e.title,
      version: e.version,
      type: e.type,
      date: e.date,
      shortText: e.shortText,
      image: abs(e.image?.url),
      contentPath: e.contentPath,
    }));
}

/** Full patch-note body as HTML (sanitised by the UI before rendering). */
export async function patchNoteBody(note: PatchNote): Promise<string> {
  if (!note.contentPath) return note.shortText ?? "";
  const body = await getJson<{ body?: string }>(P(), `${BASE}/v2/${note.contentPath.replace(/^\//, "")}`);
  return body.body ?? note.shortText ?? "";
}
