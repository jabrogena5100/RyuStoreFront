"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useAuth } from "@/components/AuthProvider";
import { ApiClientError, api } from "@/lib/api-client";
import { SHIPPING_FLAT_MINOR } from "@/lib/catalog";

/** One cart line as the screens use it (the API's LineView, with a few names the earlier screens already used). */
export interface CartLine {
  sku: string;
  slug: string;
  name: string;
  brand: string;
  /** Not in the API's cart line; the placeholder picture does not need it. */
  categorySlug: string;
  sizeLabel: string;
  colour: string;
  priceMinor: number;
  qty: number;
  /** False when the variant was withdrawn or stock fell below the quantity (docs/api/cart.md). */
  available: boolean;
  /** How high the quantity stepper may go right now. */
  maxQuantity: number;
}

interface ApiLine {
  sku: string;
  productSlug: string;
  productName: string;
  brand: string;
  sizeLabel: string;
  colour: string;
  unitPriceMinor: number;
  quantity: number;
  available: boolean;
  maxQuantity: number;
}
interface ApiCart {
  itemCount: number;
  subtotalMinor: number;
  lines: ApiLine[];
}

const fromApi = (l: ApiLine): CartLine => ({
  sku: l.sku,
  slug: l.productSlug,
  name: l.productName,
  brand: l.brand,
  categorySlug: "",
  sizeLabel: l.sizeLabel,
  colour: l.colour,
  priceMinor: l.unitPriceMinor,
  qty: l.quantity,
  available: l.available,
  maxQuantity: l.maxQuantity,
});

export type CartStatus = "loading" | "ready" | "error";

/** What a write can say back, in words the screens own. */
export type CartResult = { ok: true } | { ok: false; code: "insufficient_stock" | "unknown_item" | "max" | "network" | "error"; message: string };

interface CartApi {
  lines: CartLine[];
  count: number;
  subtotalMinor: number;
  shippingMinor: number;
  totalMinor: number;
  status: CartStatus;
  /** SKUs with a write in flight. */
  busy: ReadonlySet<string>;
  /** Adds one of this SKU (the API sets quantities, so this reads the current one first). */
  add: (sku: string) => Promise<CartResult>;
  setQty: (sku: string, qty: number) => Promise<CartResult>;
  remove: (sku: string) => Promise<CartResult>;
  refresh: () => Promise<void>;
  /** Design review only: show these lines without calling the API (cart and checkout `?state=` URLs). */
  replace: (lines: CartLine[]) => void;
}

const CartContext = createContext<CartApi | null>(null);

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("useCart must be used inside CartProvider");
  return ctx;
}

function toResult(e: unknown): CartResult {
  if (e instanceof ApiClientError) {
    if (e.code === "insufficient_stock") return { ok: false, code: "insufficient_stock", message: "There are not enough pairs in stock for that quantity." };
    if (e.code === "unknown_item") return { ok: false, code: "unknown_item", message: "This size is no longer available." };
    if (e.code === "validation_failed" && e.fields.quantity === "max") return { ok: false, code: "max", message: "You can order up to 10 of one size." };
    if (e.code === "network") return { ok: false, code: "network", message: "Could not reach the server. Nothing changed; try again." };
  }
  return { ok: false, code: "error", message: "Something went wrong. Nothing changed; try again." };
}

/**
 * The cart, kept on the server (guest cookie or signed-in cart). Reads on load and whenever the signed-in customer changes
 * (sign-in merges the guest cart, so the result is read again); every write returns the whole updated cart, which replaces local state.
 */
export function CartProvider({ children }: { children: ReactNode }) {
  const { customer } = useAuth();
  const [lines, setLines] = useState<CartLine[]>([]);
  const [status, setStatus] = useState<CartStatus>("loading");
  const [busy, setBusy] = useState<ReadonlySet<string>>(new Set());
  const fixture = useRef(false);

  const refresh = useCallback(async () => {
    if (fixture.current) return;
    try {
      const c = await api<ApiCart>("/cart");
      setLines(c.lines.map(fromApi));
      setStatus("ready");
    } catch {
      setStatus("error");
    }
  }, []);

  // Wait until the session is known (undefined), then read; read again if the customer changes (sign-in, sign-out).
  const customerId = customer === undefined ? undefined : (customer?.id ?? null);
  useEffect(() => {
    if (customerId === undefined) return;
    refresh();
  }, [customerId, refresh]);

  const write = useCallback(async (sku: string, run: () => Promise<ApiCart>): Promise<CartResult> => {
    setBusy((b) => new Set(b).add(sku));
    try {
      const c = await run();
      setLines(c.lines.map(fromApi));
      setStatus("ready");
      return { ok: true };
    } catch (e) {
      return toResult(e);
    } finally {
      setBusy((b) => {
        const n = new Set(b);
        n.delete(sku);
        return n;
      });
    }
  }, []);

  const setQty = useCallback(
    (sku: string, qty: number): Promise<CartResult> => {
      if (fixture.current) {
        setLines((cur) => (qty <= 0 ? cur.filter((l) => l.sku !== sku) : cur.map((l) => (l.sku === sku ? { ...l, qty } : l))));
        return Promise.resolve({ ok: true });
      }
      return write(sku, () => api<ApiCart>(`/cart/items/${encodeURIComponent(sku)}`, { method: "PUT", body: { quantity: qty } }));
    },
    [write],
  );

  const remove = useCallback(
    (sku: string): Promise<CartResult> => {
      if (fixture.current) {
        setLines((cur) => cur.filter((l) => l.sku !== sku));
        return Promise.resolve({ ok: true });
      }
      return write(sku, () => api<ApiCart>(`/cart/items/${encodeURIComponent(sku)}`, { method: "DELETE" }));
    },
    [write],
  );

  const linesRef = useRef(lines);
  linesRef.current = lines;
  const add = useCallback(
    (sku: string): Promise<CartResult> => {
      const have = linesRef.current.find((l) => l.sku === sku)?.qty ?? 0;
      return setQty(sku, Math.min(10, have + 1));
    },
    [setQty],
  );

  const replace = useCallback((next: CartLine[]) => {
    fixture.current = true;
    setLines(next);
    setStatus("ready");
  }, []);

  const value = useMemo<CartApi>(() => {
    const count = lines.reduce((n, l) => n + l.qty, 0);
    const subtotalMinor = lines.reduce((n, l) => n + l.priceMinor * l.qty, 0);
    const shippingMinor = lines.length ? SHIPPING_FLAT_MINOR : 0;
    return { lines, count, subtotalMinor, shippingMinor, totalMinor: subtotalMinor + shippingMinor, status, busy, add, setQty, remove, refresh, replace };
  }, [lines, status, busy, add, setQty, remove, refresh, replace]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

/** Design-review helper: fills the cart on mount so the filled state can be shown by URL (no API calls after that). */
export function SeedCart({ lines }: { lines: CartLine[] }) {
  const { replace } = useCart();
  useEffect(() => {
    replace(lines);
  }, [lines, replace]);
  return null;
}
