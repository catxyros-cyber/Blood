import { useEffect, useState } from "react";
import type { Instance } from "../core/instances/model";
import { listContent, type ContentItem, type ContentKind } from "../services/content";
import { errorMessage } from "../services/app";
import { createStore, useStore } from "../services/store";

/** Bumped whenever files in any installation change, so open lists reload. */
const contentStore = createStore<{ version: number }>({ version: 0 });

export function bumpContent() {
  contentStore.set((s) => ({ version: s.version + 1 }));
}

export function useContentVersion(): number {
  return useStore(contentStore, (s) => s.version);
}

/** Load an installation's content folder, reloading on changes. */
export function useContent(inst: Instance | undefined, kind: ContentKind) {
  const version = useContentVersion();
  const [items, setItems] = useState<ContentItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (!inst) return;
    let cancelled = false;
    setError(null);
    listContent(inst, kind)
      .then((r) => !cancelled && setItems(r))
      .catch((e) => !cancelled && setError(errorMessage(e)));
    return () => {
      cancelled = true;
    };
  }, [inst?.id, inst?.gameDir, inst?.loader, inst?.gameVersion, kind, version, tick]);
  return { items, error, reload: () => setTick((t) => t + 1) };
}
