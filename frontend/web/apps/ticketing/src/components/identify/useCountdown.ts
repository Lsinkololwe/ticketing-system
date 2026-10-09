import { useEffect, useState } from 'react';

/** Whole seconds left until `seconds` from the moment `startKey` changes. Returns 0 when done. */
export function useCountdown(seconds: number, startKey: unknown): number {
  const [left, setLeft] = useState(seconds);
  useEffect(() => {
    const end = Date.now() + seconds * 1000;
    setLeft(seconds);
    if (seconds <= 0) return;
    const t = setInterval(() => {
      const s = Math.max(0, Math.ceil((end - Date.now()) / 1000));
      setLeft(s);
      if (s === 0) clearInterval(t);
    }, 500);
    return () => clearInterval(t);
  }, [seconds, startKey]);
  return left;
}

export function formatMMSS(total: number): string {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
