"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

type Theme = "light" | "dark" | null;
const KEY = "ss-theme";
const Ctx = createContext<{ theme: Theme; toggle: () => void }>({
  theme: null,
  toggle: () => undefined,
});

function systemDark(): boolean {
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-color-scheme: dark)").matches
  );
}

/** Light/dark switch. The choice is a per-viewer convenience kept in localStorage; storage may be unavailable. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(null);
  useEffect(() => {
    try {
      const saved = window.localStorage.getItem(KEY);
      if (saved === "light" || saved === "dark") {
        setTheme(saved);
        document.documentElement.dataset.theme = saved;
      }
    } catch {
      /* storage blocked: follow the system setting */
    }
  }, []);
  const toggle = useCallback(() => {
    const root = document.documentElement;
    const dark = root.dataset.theme
      ? root.dataset.theme === "dark"
      : systemDark();
    const next: Theme = dark ? "light" : "dark";
    root.dataset.theme = next as string;
    setTheme(next);
    try {
      window.localStorage.setItem(KEY, next as string);
    } catch {
      /* ignore */
    }
  }, []);
  const value = useMemo(() => ({ theme, toggle }), [theme, toggle]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export const useTheme = () => useContext(Ctx);
