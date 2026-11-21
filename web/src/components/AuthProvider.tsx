"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, setCsrf } from "@/lib/api-client";

export interface Customer {
  id: number;
  email: string;
  displayName: string | null;
  role: "customer" | "admin";
}

interface SessionResponse {
  customer: Customer;
  csrf: { headerName: string; token: string };
}

interface AuthApi {
  /** undefined while `/session` is resolving (so the header never flashes the guest state), null for a guest. */
  customer: Customer | null | undefined;
  signIn: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName?: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthApi | null>(null);

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [customer, setCustomer] = useState<Customer | null | undefined>(undefined);

  useEffect(() => {
    let live = true;
    // /session (web/src/app/session/route.ts) answers 200 with `customer: null` for a guest, so a guest's page load has no
    // failed request in the console. Any failure is treated as a guest; the next call will show the real problem.
    fetch("/session", { headers: { Accept: "application/json" }, cache: "no-store" })
      .then(async (res) => {
        if (!res.ok) throw new Error(`session check failed: ${res.status}`);
        const body = (await res.json()) as { customer: Customer | null };
        if (live) setCustomer(body.customer);
      })
      .catch((e) => {
        if (live) {
          console.warn("session check failed", e);
          setCustomer(null);
        }
      });
    return () => {
      live = false;
    };
  }, []);

  const signIn = useCallback(async (email: string, password: string) => {
    const s = await api<SessionResponse>("/auth/login", { method: "POST", body: { email, password } });
    setCsrf(s.csrf);
    setCustomer(s.customer);
  }, []);

  const register = useCallback(async (email: string, password: string, displayName?: string) => {
    const s = await api<SessionResponse>("/auth/register", { method: "POST", body: { email, password, displayName: displayName || undefined } });
    setCsrf(s.csrf);
    setCustomer(s.customer);
  }, []);

  const signOut = useCallback(async () => {
    await api("/auth/logout", { method: "POST" });
    setCsrf(null); // the session is gone; the next write fetches a new token
    setCustomer(null);
  }, []);

  const value = useMemo(() => ({ customer, signIn, register, signOut }), [customer, signIn, register, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
