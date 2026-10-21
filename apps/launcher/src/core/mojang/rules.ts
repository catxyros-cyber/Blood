import type { Arch, OsName } from "../platform";
import type { Rule } from "./types";

export interface RuleEnv {
  os: OsName;
  arch: Arch;
  /** e.g. "10.0" — matched against `os.version` regexes. */
  osVersion?: string;
  features?: Record<string, boolean>;
}

function osMatches(name: string, env: RuleEnv): boolean {
  if (name === env.os) return true;
  if (name === "macos" && env.os === "osx") return true;
  // Some newer manifests use platform-arch names such as "windows-arm64".
  const [base, arch] = name.split("-");
  if (arch && base === env.os) return arch === env.arch || (arch === "x86_64" && env.arch === "x64");
  return false;
}

function ruleMatches(rule: Rule, env: RuleEnv): boolean {
  if (rule.os) {
    if (rule.os.name && !osMatches(rule.os.name, env)) return false;
    if (rule.os.arch) {
      // Mojang uses "x86" to mean a 32-bit JVM.
      const want = rule.os.arch;
      const have = env.arch === "x86" ? "x86" : env.arch === "arm64" ? "arm64" : "x86_64";
      if (want !== have && !(want === "x64" && have === "x86_64")) return false;
    }
    if (rule.os.version) {
      if (!env.osVersion) return false;
      try {
        if (!new RegExp(rule.os.version).test(env.osVersion)) return false;
      } catch {
        return false;
      }
    }
  }
  if (rule.features) {
    for (const [feature, wanted] of Object.entries(rule.features)) {
      if (Boolean(env.features?.[feature]) !== wanted) return false;
    }
  }
  return true;
}

/**
 * Mojang rule semantics: with no rules, allowed. Otherwise start disallowed and
 * let every matching rule set the outcome; the last match wins.
 */
export function rulesAllow(rules: Rule[] | undefined, env: RuleEnv): boolean {
  if (!rules || rules.length === 0) return true;
  let allowed = false;
  for (const rule of rules) {
    if (ruleMatches(rule, env)) allowed = rule.action === "allow";
  }
  return allowed;
}
