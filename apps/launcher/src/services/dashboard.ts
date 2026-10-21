/**
 * Facts for the Home dashboard that need a file read or a probe: which Java an
 * installation will use and whether its game files are already downloaded.
 * Everything is read from disk; nothing is estimated.
 */

import type { Instance } from "../core/instances/model";
import { app, L, P, path } from "./app";
import { native } from "./native";

export interface InstallSummary {
  /** e.g. "Java 21". Null when it isn't known until the first launch. */
  java: string | null;
  javaSource: "custom" | "managed" | "unknown";
  /** Game files (version JSON) are on disk. */
  filesReady: boolean;
  javaError?: string;
}

export async function installSummary(inst: Instance): Promise<InstallSummary> {
  const p = P();
  const text = await p.readText(path(L().versions, inst.gameVersion, `${inst.gameVersion}.json`)).catch(() => null);
  let required: number | null = null;
  if (text) {
    try {
      const v = JSON.parse(text) as { javaVersion?: { majorVersion?: number } };
      required = v.javaVersion?.majorVersion ?? 8;
    } catch {
      required = null;
    }
  }
  const custom = inst.javaPath || app.get().settings.javaPath;
  if (custom) {
    try {
      const info = await native.javaProbe(custom);
      return { java: `Java ${info.major}`, javaSource: "custom", filesReady: Boolean(text), javaError: required && info.major < required ? `Minecraft ${inst.gameVersion} needs Java ${required}` : undefined };
    } catch {
      return { java: null, javaSource: "custom", filesReady: Boolean(text), javaError: "The custom Java path can't be run" };
    }
  }
  return { java: required ? `Java ${required}` : null, javaSource: required ? "managed" : "unknown", filesReady: Boolean(text) };
}
