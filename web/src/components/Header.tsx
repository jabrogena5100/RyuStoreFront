"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import clsx from "clsx";
import { Menu, Search, ShoppingBag, X } from "lucide-react";
import { useRef } from "react";
import { OCCASIONS } from "@/lib/catalog";
import { useCart } from "@/components/CartProvider";

function SearchForm({ className }: { className?: string }) {
  return (
    <form action="/products" role="search" className={clsx("relative", className)}>
      <label htmlFor="search" className="sr-only">
        Search shoes
      </label>
      <Search aria-hidden="true" className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-fg-subtle" />
      <input
        id="search"
        name="q"
        type="search"
        placeholder="Search boots, runners, loafers"
        autoComplete="off"
        className="min-h-11 w-full rounded-md border border-line-strong bg-surface-2 pl-10 pr-3 text-small text-fg placeholder:text-fg-subtle"
      />
    </form>
  );
}

export function Header() {
  const pathname = usePathname();
  const menu = useRef<HTMLDialogElement>(null);
  const { count } = useCart();

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-canvas/85 backdrop-blur">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-surface-3 focus:px-4 focus:py-3"
      >
        Skip to content
      </a>
      <div className="mx-auto flex max-w-7xl items-center gap-4 px-4 py-3 sm:px-6 lg:px-8">
        <Link href="/" className="text-body font-semibold tracking-tight text-fg-strong">
          Storefront<span className="text-accent-text">.</span>
        </Link>

        <nav aria-label="Shop by occasion" className="ml-4 hidden items-center gap-1 lg:flex">
          {OCCASIONS.map((o) => {
            const active = pathname === "/products" && false;
            return (
              <Link
                key={o.slug}
                href={`/products?occasion=${o.slug}`}
                aria-current={active ? "page" : undefined}
                className="rounded-md px-3 py-2.5 text-small font-medium text-fg-muted transition-colors duration-(--duration-base) hover:bg-surface-2 hover:text-fg"
              >
                {o.label}
              </Link>
            );
          })}
        </nav>

        <SearchForm className="ml-auto hidden w-72 md:block" />

        <div className="ml-auto flex items-center gap-1 md:ml-0">
          <Link
            href="/cart"
            aria-label={count ? `Cart, ${count} item${count === 1 ? "" : "s"}` : "Cart, empty"}
            className="relative grid size-11 place-items-center rounded-md text-fg hover:bg-surface-2"
          >
            <ShoppingBag aria-hidden="true" className="size-5" />
            {count > 0 ? (
              <span className="tabular absolute right-1 top-1 grid min-w-5 place-items-center rounded-pill bg-accent px-1 text-caption font-semibold text-fg-strong">
                {count}
              </span>
            ) : null}
          </Link>
          <button
            type="button"
            aria-label="Open menu"
            onClick={() => menu.current?.showModal()}
            className="grid size-11 place-items-center rounded-md text-fg hover:bg-surface-2 lg:hidden"
          >
            <Menu aria-hidden="true" className="size-5" />
          </button>
        </div>
      </div>

      <dialog
        ref={menu}
        aria-label="Menu"
        onClick={(e) => {
          if (e.target === menu.current) menu.current?.close();
        }}
        className="m-0 ml-auto h-dvh w-[min(22rem,92vw)] border-l border-line bg-surface-1 p-0 text-fg shadow-overlay"
      >
        <div className="flex items-center justify-between px-4 py-3">
          <p className="text-small font-medium text-fg-muted">Shop</p>
          <button type="button" aria-label="Close menu" onClick={() => menu.current?.close()} className="grid size-11 place-items-center rounded-md hover:bg-surface-2">
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>
        <div className="px-4 pb-4 md:hidden">
          <SearchForm />
        </div>
        <nav aria-label="Menu" className="flex flex-col px-2 pb-6">
          {OCCASIONS.map((o) => (
            <Link
              key={o.slug}
              href={`/products?occasion=${o.slug}`}
              onClick={() => menu.current?.close()}
              className="flex min-h-14 flex-col justify-center rounded-md px-3 hover:bg-surface-2"
            >
              <span className="text-body font-medium text-fg-strong">{o.label}</span>
              <span className="text-small text-fg-muted">{o.blurb}</span>
            </Link>
          ))}
          <Link href="/products" onClick={() => menu.current?.close()} className="mt-2 flex min-h-12 items-center rounded-md px-3 text-small font-medium text-accent-text hover:bg-surface-2">
            All shoes
          </Link>
        </nav>
      </dialog>
    </header>
  );
}
