import { useMemo, useState } from 'react';

export type UiActor = 'alice' | 'bob';

export function useActorBucket(suffix: string, initialActor: UiActor = 'alice') {
  const [actor, setActor] = useState<UiActor>(initialActor);

  const bucket = useMemo(() => `default-${actor}-${suffix}`, [actor, suffix]);

  return {
    actor,
    setActor,
    bucket
  };
}
