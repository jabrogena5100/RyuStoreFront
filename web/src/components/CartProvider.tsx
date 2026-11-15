"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { SHIPPING_FLAT_MINOR, type Product } from "@/lib/catalog";

export interface CartLine {
  sku: string;
  slug: string;
  name: string;
  brand: string;
  categorySlug: string;
  sizeLabel: string;
  colour: string;
  priceMinor: number;
  qty: number;
}

interface CartApi {
  lines: CartLine[];
  count: number;
  subtotalMinor: number;
  shippingMinor: number;
  totalMinor: number;
  add: (product: Product, sku: string) => void;
  setQty: (sku: string, qty: number) => void;
  remove: (sku: string) => void;
  replace: (lines: CartLine[]) => void;
}

const CartContext = createContext<CartApi | null>(null);

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("useCart must be used inside CartProvider");
  return ctx;
}

/** In-memory cart for the design phase. The real cart is server-side (guest cookie row); the shape of a line matches the API. */
export function CartProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<CartLine[]>([]);

  const add = useCallback((product: Product, sku: string) => {
    const v = product.variants.find((x) => x.sku === sku);
    if (!v) return;
    setLines((cur) => {
      const found = cur.find((l) => l.sku === sku);
      if (found) return cur.map((l) => (l.sku === sku ? { ...l, qty: Math.min(10, l.qty + 1) } : l));
      return [
        ...cur,
        {
          sku,
          slug: product.slug,
          name: product.name,
          brand: product.brand,
          categorySlug: product.categorySlug,
          sizeLabel: v.sizeLabel,
          colour: v.colour,
          priceMinor: v.priceMinor,
          qty: 1,
        },
      ];
    });
  }, []);
  const setQty = useCallback(
    (sku: string, qty: number) => setLines((cur) => cur.map((l) => (l.sku === sku ? { ...l, qty: Math.max(1, Math.min(10, qty)) } : l))),
    [],
  );
  const remove = useCallback((sku: string) => setLines((cur) => cur.filter((l) => l.sku !== sku)), []);
  const replace = useCallback((next: CartLine[]) => setLines(next), []);

  const value = useMemo<CartApi>(() => {
    const subtotal = lines.reduce((sum, l) => sum + l.priceMinor * l.qty, 0);
    const shipping = lines.length ? SHIPPING_FLAT_MINOR : 0;
    return {
      lines,
      count: lines.reduce((n, l) => n + l.qty, 0),
      subtotalMinor: subtotal,
      shippingMinor: shipping,
      totalMinor: subtotal + shipping,
      add,
      setQty,
      remove,
      replace,
    };
  }, [lines, add, setQty, remove, replace]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

/** Design-review helper: fills the cart on mount so the filled state can be shown by URL. */
export function SeedCart({ lines }: { lines: CartLine[] }) {
  const { replace } = useCart();
  useEffect(() => {
    replace(lines);
  }, [lines, replace]);
  return null;
}
