"use client";

import Link from "next/link";
import { ShoppingBag } from "lucide-react";
import { useCart } from "@/components/CartProvider";

/** The only client part of the header: it needs the cart count. */
export function CartLink() {
  const { count } = useCart();
  return (
    <Link
      href="/cart"
      className="relative inline-flex min-h-11 items-center gap-2 rounded-md px-3 text-small font-medium text-fg transition-colors duration-(--duration-base) hover:bg-surface-2"
    >
      <ShoppingBag aria-hidden="true" className="size-5" />
      <span className="sr-only sm:not-sr-only">Cart</span>
      {count > 0 ? (
        <span className="tabular grid min-w-5 place-items-center rounded-pill bg-accent px-1.5 text-caption font-semibold text-fg-strong">
          {count}
          <span className="sr-only"> {count === 1 ? "item" : "items"}</span>
        </span>
      ) : null}
    </Link>
  );
}
