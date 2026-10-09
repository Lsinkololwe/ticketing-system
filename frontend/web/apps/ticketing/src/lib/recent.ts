"use client";

import { useEffect, useState } from "react";
import type { EventCardRow } from "@pml.tickets/shared";

const KEY = "ss-viewed";
const MAX = 8;

/** Remember an event the buyer opened (per-viewer convenience, kept in localStorage). */
export function rememberViewed(e: EventCardRow) {
  try {
    const list = (
      JSON.parse(window.localStorage.getItem(KEY) ?? "[]") as EventCardRow[]
    ).filter((x) => x.id !== e.id);
    window.localStorage.setItem(
      KEY,
      JSON.stringify([e, ...list].slice(0, MAX)),
    );
  } catch {
    /* storage unavailable */
  }
}

export function useRecentlyViewed(): EventCardRow[] {
  const [list, setList] = useState<EventCardRow[]>([]);
  useEffect(() => {
    try {
      const raw = JSON.parse(window.localStorage.getItem(KEY) ?? "[]");
      if (Array.isArray(raw)) setList(raw as EventCardRow[]);
    } catch {
      setList([]);
    }
  }, []);
  return list;
}
