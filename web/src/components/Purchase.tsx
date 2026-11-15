"use client";

import clsx from "clsx";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { Check, ShoppingBag } from "lucide-react";
import { useId, useState } from "react";
import { useCart } from "@/components/CartProvider";
import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";
import type { Product } from "@/lib/catalog";

/** Size picker plus add to cart: native radio inputs (arrow keys and screen readers for free), one shared selection indicator. */
export function Purchase({ product }: { product: Product }) {
  const groupId = useId();
  const [sku, setSku] = useState<string | null>(null);
  const [missing, setMissing] = useState(false);
  const [added, setAdded] = useState(false);
  const { add } = useCart();
  const { show } = useToast();
  const reduce = useReducedMotion();

  const soldOut = !product.inStock;

  function submit() {
    if (!sku) {
      setMissing(true);
      return;
    }
    const v = product.variants.find((x) => x.sku === sku);
    add(product, sku);
    show(`Added ${product.name}, size ${v?.sizeLabel}`);
    setAdded(true);
    window.setTimeout(() => setAdded(false), 1800);
  }

  return (
    <div className="flex flex-col gap-5">
      <fieldset>
        <div className="mb-3 flex items-baseline justify-between">
          <legend className="text-small font-medium text-fg">Size (US)</legend>
          <span className="text-small text-fg-subtle capitalize">{product.gender === "unisex" ? "Unisex sizing" : `${product.gender}'s sizing`}</span>
        </div>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(3.5rem,1fr))] gap-2">
          {product.variants.map((v) => {
            const selected = sku === v.sku;
            return (
              <label
                key={v.sku}
                className={clsx(
                  "relative grid min-h-12 cursor-pointer place-items-center rounded-md border text-small font-medium has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-accent-text",
                  "transition-colors duration-(--duration-base)",
                  v.available
                    ? selected
                      ? "border-accent-line text-accent-text"
                      : "border-line-strong text-fg hover:bg-surface-2"
                    : "cursor-not-allowed border-line text-fg-subtle line-through",
                )}
              >
                <input
                  type="radio"
                  name={groupId}
                  value={v.sku}
                  disabled={!v.available}
                  checked={selected}
                  onChange={() => {
                    setSku(v.sku);
                    setMissing(false);
                  }}
                  className="sr-only"
                  aria-label={`Size ${v.sizeLabel}${v.available ? "" : ", sold out"}`}
                />
                {selected ? (
                  <motion.span
                    layoutId={reduce ? undefined : `size-${product.id}`}
                    transition={{ duration: 0.2, ease: [0.2, 0, 0, 1] }}
                    className="absolute inset-0 rounded-md bg-accent-soft"
                    aria-hidden="true"
                  />
                ) : null}
                <span className="tabular relative">{v.sizeLabel}</span>
              </label>
            );
          })}
        </div>
        <p className="mt-2 min-h-5 text-small text-danger" role={missing ? "alert" : undefined}>
          {missing ? "Choose a size to continue." : ""}
        </p>
      </fieldset>

      <Button size="lg" onClick={submit} disabled={soldOut} className="w-full">
        <AnimatePresence mode="wait" initial={false}>
          <motion.span
            key={soldOut ? "sold" : added ? "added" : "add"}
            initial={reduce ? false : { opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={reduce ? { opacity: 0 } : { opacity: 0, y: -6 }}
            transition={{ duration: 0.16 }}
            className="inline-flex items-center gap-2"
          >
            {soldOut ? (
              "Sold out"
            ) : added ? (
              <>
                <Check aria-hidden="true" className="size-5" /> Added to cart
              </>
            ) : (
              <>
                <ShoppingBag aria-hidden="true" className="size-5" /> Add to cart
              </>
            )}
          </motion.span>
        </AnimatePresence>
      </Button>
    </div>
  );
}
