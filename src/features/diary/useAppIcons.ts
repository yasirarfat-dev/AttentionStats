import { useEffect, useState } from 'react';
import NativeDiary from '../../platform/specs/NativeDiary';

const cache = new Map<string, string>();

/** Launcher icons as data URIs, fetched once per package per process. */
export function useAppIcons(packages: string[]): Record<string, string> {
  const key = [...new Set(packages)].sort().join('|');
  const [icons, setIcons] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      packages.filter(p => cache.has(p)).map(p => [p, cache.get(p)!]),
    ),
  );
  useEffect(() => {
    let active = true;
    const wanted = key ? key.split('|') : [];
    const missing = wanted.filter(name => !cache.has(name));
    const publish = () => {
      if (active)
        setIcons(
          Object.fromEntries(
            wanted.filter(p => cache.has(p)).map(p => [p, cache.get(p)!]),
          ),
        );
    };
    if (!missing.length || !NativeDiary) {
      publish();
      return;
    }
    NativeDiary.getAppIcons(missing)
      .then(raw => {
        const parsed = JSON.parse(raw) as Record<string, unknown>;
        for (const [name, uri] of Object.entries(parsed))
          if (
            typeof uri === 'string' &&
            uri.startsWith('data:image/png;base64,')
          )
            cache.set(name, uri);
        publish();
      })
      .catch(publish);
    return () => {
      active = false;
    };
  }, [key]);
  return icons;
}
