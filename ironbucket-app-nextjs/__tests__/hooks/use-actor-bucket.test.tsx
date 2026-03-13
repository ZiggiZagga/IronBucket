/** @jest-environment jsdom */
import { act, renderHook } from '@testing-library/react';
import { useActorBucket } from '@/hooks/useActorBucket';

describe('useActorBucket', () => {
  it('starts with alice and computes bucket', () => {
    const { result } = renderHook(() => useActorBucket('files'));
    expect(result.current.actor).toBe('alice');
    expect(result.current.bucket).toBe('default-alice-files');
  });

  it('updates bucket when actor changes', () => {
    const { result } = renderHook(() => useActorBucket('methods'));

    act(() => {
      result.current.setActor('bob');
    });

    expect(result.current.actor).toBe('bob');
    expect(result.current.bucket).toBe('default-bob-methods');
  });
});
