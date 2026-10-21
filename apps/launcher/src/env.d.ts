/** Build-time configuration (set in apps/launcher/.env or the CI environment). */
interface ImportMetaEnv {
  /** Azure application (client) id used for Microsoft sign-in. */
  readonly VITE_MS_CLIENT_ID?: string;
  /** Base URL of the BLOOD API, e.g. https://bloomlauncher.example. Empty = online features off. */
  readonly VITE_BLOOM_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module "*.css";
