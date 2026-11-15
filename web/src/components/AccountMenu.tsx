"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ChevronDown, User } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/components/AuthProvider";

const item = "flex min-h-11 w-full items-center rounded-md px-3 text-small text-fg transition-colors duration-(--duration-base) hover:bg-surface-3";

/** Header account control: reserves its width while the session loads, "Sign in" for guests, a native <details> menu when signed in. */
export function AccountMenu() {
  const { customer, signOut } = useAuth();
  const router = useRouter();
  const menu = useRef<HTMLDetailsElement>(null);
  const [failed, setFailed] = useState(false);

  // <details> has no light dismiss of its own: close it on an outside click and on Escape.
  useEffect(() => {
    const close = (e: Event) => {
      if (menu.current?.open && !menu.current.contains(e.target as Node)) menu.current.open = false;
    };
    const esc = (e: KeyboardEvent) => {
      if (e.key === "Escape" && menu.current?.open) {
        menu.current.open = false;
        menu.current.querySelector("summary")?.focus();
      }
    };
    document.addEventListener("pointerdown", close);
    document.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("pointerdown", close);
      document.removeEventListener("keydown", esc);
    };
  }, []);

  if (customer === undefined) return <span aria-hidden="true" className="inline-block h-11 w-11 sm:w-24" />;
  if (customer === null) {
    return (
      <Link href="/sign-in" className="inline-flex min-h-11 items-center gap-2 rounded-md px-3 text-small font-medium text-fg transition-colors duration-(--duration-base) hover:bg-surface-2">
        <User aria-hidden="true" className="size-5" />
        <span className="sr-only sm:not-sr-only">Sign in</span>
      </Link>
    );
  }
  const name = customer.displayName ?? customer.email.split("@")[0];
  return (
    <details ref={menu} className="relative">
      <summary className="inline-flex min-h-11 cursor-pointer list-none items-center gap-2 rounded-md px-3 text-small font-medium text-fg transition-colors duration-(--duration-base) hover:bg-surface-2 [&::-webkit-details-marker]:hidden">
        <User aria-hidden="true" className="size-5" />
        <span className="sr-only max-w-28 truncate sm:not-sr-only">{name}</span>
        <ChevronDown aria-hidden="true" className="hidden size-4 text-fg-subtle sm:block" />
      </summary>
      <div className="absolute right-0 top-full z-40 mt-1 w-60 rounded-lg border border-line bg-surface-2 p-2 shadow-overlay">
        <p className="truncate px-3 py-2 text-caption text-fg-subtle">Signed in as {customer.email}</p>
        <Link href="/orders" className={item} onClick={() => menu.current && (menu.current.open = false)}>
          Your orders
        </Link>
        {customer.role === "admin" ? (
          <Link href="/admin/orders" className={item} onClick={() => menu.current && (menu.current.open = false)}>
            Admin
          </Link>
        ) : null}
        <button
          type="button"
          className={item}
          onClick={async () => {
            setFailed(false);
            try {
              await signOut();
              if (menu.current) menu.current.open = false;
              router.push("/");
              router.refresh();
            } catch {
              setFailed(true);
            }
          }}
        >
          Sign out
        </button>
        {failed ? <p role="alert" className="px-3 py-2 text-small text-danger">Could not sign out. Try again.</p> : null}
      </div>
    </details>
  );
}
