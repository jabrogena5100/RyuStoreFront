"use client";

/*
 * Cart drawer. Adapted from "Drawer" by ddoemonn on 21st.dev (https://21st.dev/@ddoemonn/components/drawer), licence
 * shown there: MIT. Kept from the original: focus moves into the panel on open and back to the opener on close, Tab is
 * trapped inside, everything else on the page is made inert, page scroll is locked, Escape closes. Changed: the motion
 * library and its spring are replaced by a 240 ms CSS transform (removed under reduced motion), drag-to-dismiss is dropped,
 * and the panel uses the design tokens. See design/COMPONENT-NOTES.md.
 */
import Link from "next/link";
import { X } from "lucide-react";
import { createContext, useCallback, useContext, useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useCart } from "@/components/CartProvider";
import { ProductImage } from "@/components/ProductImage";
import { ButtonLink } from "@/components/ui/Button";
import { Price } from "@/components/ui/Price";

const FOCUSABLE = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])';

const DrawerContext = createContext<{ openDrawer: () => void } | null>(null);

export function useCartDrawer() {
  const ctx = useContext(DrawerContext);
  if (!ctx) throw new Error("useCartDrawer must be used inside CartDrawerProvider");
  return ctx;
}

export function CartDrawerProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const openDrawer = useCallback(() => setOpen(true), []);
  return (
    <DrawerContext.Provider value={{ openDrawer }}>
      {children}
      <CartDrawer open={open} onClose={() => setOpen(false)} />
    </DrawerContext.Provider>
  );
}

function CartDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { lines, count, subtotalMinor } = useCart();
  const titleId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const returnTo = useRef<HTMLElement | null>(null);
  const [host, setHost] = useState<HTMLElement | null>(null);

  useEffect(() => setHost(document.body), []);

  // Focus in on open, back to the opener on close.
  useEffect(() => {
    if (open) {
      returnTo.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      const panel = panelRef.current;
      (panel?.querySelector<HTMLElement>(FOCUSABLE) ?? panel)?.focus({ preventScroll: true });
      return;
    }
    const target = returnTo.current;
    returnTo.current = null;
    if (target?.isConnected) target.focus({ preventScroll: true });
  }, [open]);

  // Scroll lock and inert background while open.
  useEffect(() => {
    if (!open) return;
    const root = document.documentElement;
    const overflow = root.style.overflow;
    root.style.overflow = "hidden";
    const muted: HTMLElement[] = [];
    for (const node of Array.from(document.body.children)) {
      if (!(node instanceof HTMLElement) || node.contains(rootRef.current) || node.inert) continue;
      node.inert = true;
      muted.push(node);
    }
    return () => {
      root.style.overflow = overflow;
      for (const el of muted) el.inert = false;
    };
  }, [open]);

  function onKeyDown(e: KeyboardEvent) {
    if (e.key === "Escape") {
      e.stopPropagation();
      onClose();
      return;
    }
    if (e.key !== "Tab" || !panelRef.current) return;
    const nodes = Array.from(panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLE));
    if (nodes.length === 0) return;
    const first = nodes[0];
    const last = nodes[nodes.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  }

  if (!host) return null;
  return createPortal(
    <div ref={rootRef} className={open ? "fixed inset-0 z-50" : "pointer-events-none invisible fixed inset-0 z-50 transition-[visibility] delay-(--duration-slow)"} inert={!open}>
      <div
        aria-hidden="true"
        onClick={onClose}
        className={`absolute inset-0 bg-canvas/70 transition-opacity duration-(--duration-slow) ease-standard ${open ? "opacity-100" : "opacity-0"}`}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onKeyDown={onKeyDown}
        className={`absolute inset-y-0 right-0 flex w-full max-w-md flex-col border-l border-line bg-surface-1 shadow-overlay outline-none transition-transform duration-(--duration-slow) ease-standard ${open ? "translate-x-0" : "translate-x-full"}`}
      >
        <header className="flex items-center justify-between gap-3 border-b border-line px-5 py-4">
          <div>
            <p className="font-mono text-caption uppercase text-fg-muted">Your bag</p>
            <h2 id={titleId} className="text-h2 font-bold text-fg-strong">
              {count} {count === 1 ? "pair" : "pairs"}
            </h2>
          </div>
          <button type="button" onClick={onClose} aria-label="Close bag" className="grid size-11 place-items-center rounded-md text-fg-muted transition-colors duration-(--duration-base) hover:bg-surface-2 hover:text-fg">
            <X aria-hidden="true" className="size-5" />
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-4">
          {lines.length === 0 ? (
            <p className="py-10 text-center text-body text-fg-muted">Your bag is empty.</p>
          ) : (
            <ul className="flex flex-col divide-y divide-line">
              {lines.map((l) => (
                <li key={l.sku} className="flex gap-4 py-4 first:pt-0">
                  <div className="w-20 shrink-0 overflow-hidden rounded-sm">
                    <ProductImage product={l} label={false} decorative />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="font-mono text-caption uppercase text-fg-muted">{l.brand}</p>
                    <Link href={`/products/${l.slug}`} onClick={onClose} className="mt-0.5 block truncate text-small font-medium text-fg-strong hover:underline">
                      {l.name}
                    </Link>
                    <p className="mt-0.5 text-small capitalize text-fg-muted">
                      Size {l.sizeLabel} · {l.colour} · Qty {l.qty}
                    </p>
                    {!l.available ? <p className="mt-1 text-small text-warning">Check this line in your bag</p> : null}
                  </div>
                  <Price minor={l.priceMinor * l.qty} className="shrink-0 text-small" />
                </li>
              ))}
            </ul>
          )}
        </div>

        <footer className="border-t border-line px-5 py-4">
          <div className="flex items-baseline justify-between">
            <span className="text-small text-fg-muted">Subtotal</span>
            <Price minor={subtotalMinor} className="text-body" />
          </div>
          <p className="mt-1 text-small text-fg-muted">Flat shipping is added at checkout.</p>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <ButtonLink href="/cart" variant="secondary" size="lg" onClick={onClose}>
              View bag
            </ButtonLink>
            <ButtonLink href="/checkout" size="lg" onClick={onClose} aria-disabled={lines.length === 0 || undefined}>
              Checkout
            </ButtonLink>
          </div>
        </footer>
      </div>
    </div>,
    host,
  );
}
