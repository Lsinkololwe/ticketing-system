"use client";

import { useEffect, useState } from "react";

/** The reservation the buyer is paying for, remembered for this tab so the header can offer "Checkout 9:41". */
export interface HeldReservation {
  reservationId: string;
  eventId: string;
  expiresAt: string;
}
const KEY = "ss-hold";
const EVT = "ss-hold-change";

export function readHold(): HeldReservation | null {
  try {
    const raw = window.sessionStorage.getItem(KEY);
    if (!raw) return null;
    const h = JSON.parse(raw) as HeldReservation;
    return h && h.reservationId && Date.parse(h.expiresAt) > Date.now()
      ? h
      : null;
  } catch {
    return null;
  }
}
export function writeHold(h: HeldReservation | null) {
  try {
    if (h) window.sessionStorage.setItem(KEY, JSON.stringify(h));
    else window.sessionStorage.removeItem(KEY);
  } catch {
    /* storage unavailable: the header chip is a convenience only */
  }
  window.dispatchEvent(new Event(EVT));
}

export function useHeldReservation(): HeldReservation | null {
  const [hold, setHold] = useState<HeldReservation | null>(null);
  useEffect(() => {
    const sync = () => setHold(readHold());
    sync();
    window.addEventListener(EVT, sync);
    const t = window.setInterval(sync, 1000);
    return () => {
      window.removeEventListener(EVT, sync);
      window.clearInterval(t);
    };
  }, []);
  return hold;
}
