"use client";

import { useEffect, useState } from "react";

/** Current time in ms, refreshed every `every` ms while `active`. */
export function useNow(every = 1000, active = true): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    setNow(Date.now());
    const t = window.setInterval(() => setNow(Date.now()), every);
    return () => window.clearInterval(t);
  }, [every, active]);
  return now;
}
