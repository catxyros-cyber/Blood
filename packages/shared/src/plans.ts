/**
 * MOTION Premium plans and the Free vs Premium feature comparison. Prices are
 * NOT defined here: they come from Stripe via the API (configured with the
 * STRIPE_PRICE_* environment variables), so they can change without a release.
 */

export type PlanId = "monthly" | "yearly" | "lifetime";

export interface PlanInfo {
  id: PlanId;
  name: string;
  /** null for the one-time purchase. */
  interval: "month" | "year" | null;
  /** In the smallest currency unit, e.g. cents. */
  amount: number;
  currency: string;
  /** Present when the server has this plan configured in Stripe. */
  available: boolean;
}

export const PLAN_NAMES: Record<PlanId, string> = {
  monthly: "Premium Monthly",
  yearly: "Premium Yearly",
  lifetime: "Premium Lifetime",
};

export type FeatureKey =
  | "launcher"
  | "modrinth"
  | "manual_mods"
  | "basic_presets"
  | "basic_stats"
  | "standard_themes"
  | "advanced_presets"
  | "advanced_analytics"
  | "custom_themes"
  | "install_tools"
  | "cloud_sync"
  | "cosmetics"
  | "early_access";

export interface FeatureRow {
  key: FeatureKey;
  title: string;
  description: string;
  free: boolean;
  premium: boolean;
}

/** Core launching and ordinary mod management are always free. */
export const FEATURES: FeatureRow[] = [
  { key: "launcher", title: "Unlimited installations", description: "Vanilla, Fabric, Quilt, Forge and NeoForge, each with its own folders.", free: true, premium: true },
  { key: "modrinth", title: "Modrinth browser", description: "Search, install, update and remove mods, packs, shaders and modpacks.", free: true, premium: true },
  { key: "manual_mods", title: "Your own mods", description: "Drag and drop .jar files, import folders, toggle and remove mods.", free: true, premium: true },
  { key: "basic_presets", title: "Potato and Balanced presets", description: "Reversible video presets with automatic backups.", free: true, premium: true },
  { key: "basic_stats", title: "Process stats", description: "Memory, CPU and launch time for running games.", free: true, premium: true },
  { key: "standard_themes", title: "Themes & backgrounds", description: "Crimson, Obsidian, Ash, Ember and Monochrome themes, MOTION's accent palette and your own background image.", free: true, premium: true },
  { key: "advanced_presets", title: "Competitive and Extreme FPS presets", description: "Aggressive, fully reversible settings plus a custom preset editor.", free: false, premium: true },
  { key: "advanced_analytics", title: "Advanced performance analytics", description: "Measured heap usage, GC pause times and stutter detection from the JVM's own log.", free: false, premium: true },
  { key: "custom_themes", title: "Any accent colour", description: "Pick any accent colour for the launcher with the colour picker.", free: false, premium: true },
  { key: "install_tools", title: "Installation tools", description: "Bulk mod updates and one-click profile cloning with settings.", free: false, premium: true },
  { key: "cloud_sync", title: "Cloud sync", description: "Launcher settings and installation profiles on every PC you sign in to.", free: false, premium: true },
  { key: "cosmetics", title: "Supporter badge", description: "A Premium badge and profile flair in the launcher.", free: false, premium: true },
  { key: "early_access", title: "Early access", description: "Try selected new MOTION features before everyone else.", free: false, premium: true },
];

export function formatPrice(amount: number, currency: string, locale?: string): string {
  try {
    return new Intl.NumberFormat(locale, { style: "currency", currency: currency.toUpperCase() }).format(amount / 100);
  } catch {
    return `${(amount / 100).toFixed(2)} ${currency.toUpperCase()}`;
  }
}
