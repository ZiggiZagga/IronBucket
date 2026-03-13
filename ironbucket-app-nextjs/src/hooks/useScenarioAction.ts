import { useState } from 'react';

export function useScenarioAction<T>() {
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [result, setResult] = useState<T | null>(null);
  const [isRunning, setIsRunning] = useState(false);

  const run = async (action: () => Promise<T>, successMessage?: (value: T) => string) => {
    setStatus('');
    setError('');
    setResult(null);
    setIsRunning(true);

    try {
      const value = await action();
      setResult(value);
      if (successMessage) {
        setStatus(successMessage(value));
      }
      return value;
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
      throw nextError;
    } finally {
      setIsRunning(false);
    }
  };

  return {
    status,
    setStatus,
    error,
    setError,
    result,
    setResult,
    isRunning,
    run
  };
}
