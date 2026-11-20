"use client";

import Link from "next/link";
import { Minus, Plus, X } from "lucide-react";
import { useState } from "react";
import { useCart, type CartResult } from "@/components/CartProvider";
import { Alert } from "@/components/ui/Alert";
import { ProductImage } from "@/components/ProductImage";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Price } from "@/components/ui/Price";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/StatePanel";
import { formatPrice } from "@/lib/catalog";

export function CartView({ state }: { state?: string }) {
  const { lines, subtotalMinor, shippingMinor, totalMinor, setQty, remove, count, status, busy, refresh } = useCart();
  const [problem, setProblem] = useState<string | null>(null);

  async function act(run: Promise<CartResult>) {
    setProblem(null);
    const r = await run;
    if (!r.ok) setProblem(r.message);
  }

  if (state === "loading" || (!state && status === "loading")) {
    return (
      <div role="status" className="grid gap-8 lg:grid-cols-[1fr_22rem]" aria-busy="true" aria-label="Loading your cart">
        <div className="flex flex-col gap-4">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex gap-4 rounded-lg border border-line p-4">
              <Skeleton className="h-28 w-24" />
              <div className="flex flex-1 flex-col gap-3">
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-5 w-48" />
                <Skeleton className="h-4 w-32" />
              </div>
            </div>
          ))}
        </div>
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }
  if (state === "error" || (!state && status === "error")) {
    return (
      <ErrorState title="We could not load your cart" action={<Button onClick={() => (state ? window.location.reload() : refresh())}>Try again</Button>}>
        Your items are safe. Check your connection and try again.
      </ErrorState>
    );
  }
  if (lines.length === 0) {
    return (
      <EmptyState title="Your cart is empty" action={<ButtonLink href="/products">Browse all shoes</ButtonLink>}>
        Add a pair and it will wait here. Pick an occasion to start quickly.
      </EmptyState>
    );
  }

  return (
    <div className="grid items-start gap-8 lg:grid-cols-[1fr_22rem]">
      <div className="flex flex-col gap-4">
      {problem ? <Alert tone="danger" title="That change was not saved">{problem}</Alert> : null}
      <ul className="flex flex-col gap-4" aria-label="Items in your cart">
        {lines.map((l) => (
            <li
              key={l.sku}
              className="flex gap-4 rounded-lg border border-line bg-surface-1 p-3 sm:p-4"
            >
              <Link href={`/products/${l.slug}`} className="w-24 shrink-0 overflow-hidden rounded-md border border-line sm:w-28" aria-label={`${l.name} product page`}>
                <ProductImage product={{ brand: l.brand, name: l.name, categorySlug: l.categorySlug }} label={false} decorative />
              </Link>
              <div className="flex min-w-0 flex-1 flex-col">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-caption font-medium uppercase text-fg-muted">{l.brand}</p>
                    <p className="truncate text-body font-medium text-fg-strong">{l.name}</p>
                    <p className="text-small text-fg-muted">
                      <span className="capitalize">{l.colour}</span> · Size {l.sizeLabel}
                    </p>
                    {!l.available ? <p className="mt-1 text-small font-medium text-warning">No longer available in this quantity. Remove it or lower the quantity to check out.</p> : null}
                  </div>
                  <button
                    type="button"
                    aria-label={`Remove ${l.name}, size ${l.sizeLabel}`}
                    onClick={() => act(remove(l.sku))} disabled={busy.has(l.sku)}
                    className="-mr-2 -mt-2 grid size-11 shrink-0 place-items-center rounded-md text-fg-subtle hover:bg-surface-3 hover:text-fg"
                  >
                    <X aria-hidden="true" className="size-5" />
                  </button>
                </div>
                <div className="mt-auto flex items-end justify-between pt-3">
                  <div role="group" aria-label={`Quantity for ${l.name}`} className="inline-flex items-center rounded-md border border-line-control">
                    <button type="button" aria-label="Decrease quantity" disabled={l.qty <= 1 || busy.has(l.sku)} onClick={() => act(setQty(l.sku, l.qty - 1))} className="grid size-11 place-items-center rounded-l-md hover:bg-surface-3 disabled:opacity-40">
                      <Minus aria-hidden="true" className="size-4" />
                    </button>
                    <span className="tabular w-8 text-center text-small font-medium" aria-live="polite">
                      {l.qty}
                    </span>
                    <button type="button" aria-label="Increase quantity" disabled={l.qty >= Math.min(10, l.maxQuantity || 10) || !l.available || busy.has(l.sku)} onClick={() => act(setQty(l.sku, l.qty + 1))} className="grid size-11 place-items-center rounded-r-md hover:bg-surface-3 disabled:opacity-40">
                      <Plus aria-hidden="true" className="size-4" />
                    </button>
                  </div>
                  <Price minor={l.priceMinor * l.qty} className="text-body" />
                </div>
              </div>
            </li>
          ))}
      </ul>
      </div>

      <aside aria-label="Order summary" className="rounded-lg border border-line bg-surface-1 p-5 lg:sticky lg:top-6">
        <h2 className="text-h2 font-semibold text-fg-strong">Summary</h2>
        <dl className="mt-4 flex flex-col gap-2 text-small">
          <div className="flex justify-between">
            <dt className="text-fg-muted">Subtotal ({count} {count === 1 ? "item" : "items"})</dt>
            <dd className="tabular text-fg">{formatPrice(subtotalMinor)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-fg-muted">Shipping (flat rate)</dt>
            <dd className="tabular text-fg">{formatPrice(shippingMinor)}</dd>
          </div>
          <div className="mt-2 flex justify-between border-t border-line pt-3 text-body">
            <dt className="font-medium text-fg-strong">Total</dt>
            <dd className="tabular font-semibold text-fg-strong">{formatPrice(totalMinor)}</dd>
          </div>
        </dl>
        <ButtonLink href="/checkout" size="lg" className="mt-5 w-full">
          Checkout
        </ButtonLink>
        <p className="mt-3 text-caption text-fg-muted">Stripe test mode: no real payment is taken.</p>
      </aside>
    </div>
  );
}
