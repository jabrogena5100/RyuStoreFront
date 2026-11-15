"use client";

import clsx from "clsx";
import { Check, ShoppingBag } from "lucide-react";
import { useId, useMemo, useState } from "react";
import { useCart } from "@/components/CartProvider";
import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";
import type { ProductDetail } from "@/lib/catalog";

/**
 * Colour and size pickers plus add to cart. Real variants are size x colour, so the colour is chosen first (only when there is
 * more than one) and the sizes shown are that colour's. Native radio inputs: arrow keys and screen readers for free.
 */
export function Purchase({ product }: { product: ProductDetail }) {
  const groupId = useId();
  const colourGroupId = useId();
  // Colours in the API's order; start on the first one that has a pair left.
  const colours = useMemo(() => [...new Set(product.variants.map((v) => v.colour))], [product.variants]);
  const [colour, setColour] = useState(() => colours.find((c) => product.variants.some((v) => v.colour === c && v.available)) ?? colours[0]);
  const [sku, setSku] = useState<string | null>(null);
  const [missing, setMissing] = useState(false);
  const [added, setAdded] = useState(false);
  const [adding, setAdding] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const { add } = useCart();
  const { show } = useToast();

  const soldOut = !product.variants.some((v) => v.available);
  const sizes = useMemo(
    () => product.variants.filter((v) => v.colour === colour).sort((a, b) => Number.parseFloat(a.sizeLabel) - Number.parseFloat(b.sizeLabel)),
    [product.variants, colour],
  );

  async function submit() {
    if (!sku) {
      setMissing(true);
      return;
    }
    const v = product.variants.find((x) => x.sku === sku);
    setProblem(null);
    setAdding(true);
    const result = await add(sku);
    setAdding(false);
    if (!result.ok) {
      setProblem(result.message);
      return;
    }
    show(`Added ${product.name}, size ${v?.sizeLabel}${colours.length > 1 ? `, ${v?.colour}` : ""}`);
    setAdded(true);
    window.setTimeout(() => setAdded(false), 1800);
  }

  return (
    <div className="flex flex-col gap-5">
      {colours.length > 1 ? (
        <fieldset>
          <legend className="mb-3 text-small font-medium text-fg">
            Colour: <span className="font-normal capitalize text-fg-muted">{colour}</span>
          </legend>
          <div className="flex flex-wrap gap-2">
            {colours.map((c) => {
              const any = product.variants.some((v) => v.colour === c && v.available);
              const selected = c === colour;
              return (
                <label
                  key={c}
                  className={clsx(
                    "inline-flex min-h-11 cursor-pointer items-center rounded-pill border px-4 text-small font-medium capitalize has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-accent-text",
                    "transition-colors duration-(--duration-base)",
                    selected ? "border-accent-line bg-accent-soft text-accent-text" : "border-line-strong text-fg hover:bg-surface-2",
                    !any && "text-fg-subtle line-through",
                  )}
                >
                  <input
                    type="radio"
                    name={colourGroupId}
                    value={c}
                    checked={selected}
                    onChange={() => {
                      setColour(c);
                      setSku(null);
                      setMissing(false);
                    }}
                    className="sr-only"
                    aria-label={`${c}${any ? "" : ", sold out"}`}
                  />
                  {c}
                </label>
              );
            })}
          </div>
        </fieldset>
      ) : null}
      <fieldset>
        <div className="mb-3 flex items-baseline justify-between">
          <legend className="text-small font-medium text-fg">Size (US)</legend>
          <span className="text-small text-fg-subtle capitalize">{product.gender === "unisex" ? "Unisex sizing" : `${product.gender}'s sizing`}</span>
        </div>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(3.5rem,1fr))] gap-2">
          {sizes.map((v) => {
            const selected = sku === v.sku;
            return (
              <label
                key={v.sku}
                className={clsx(
                  "relative grid min-h-12 cursor-pointer place-items-center rounded-md border text-small font-medium has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-accent-text",
                  "transition-colors duration-(--duration-base)",
                  v.available
                    ? selected
                      ? "border-accent-line bg-accent-soft text-accent-text"
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
                    setProblem(null);
                  }}
                  className="sr-only"
                  aria-label={`Size ${v.sizeLabel}${v.available ? "" : ", sold out"}`}
                />
                <span className="tabular">{v.sizeLabel}</span>
              </label>
            );
          })}
        </div>
        <p className="mt-2 min-h-5 text-small text-danger" role={missing ? "alert" : undefined}>
          {missing ? "Choose a size to continue." : ""}
        </p>
      </fieldset>

      {problem ? (
        <p role="alert" className="-mt-2 text-small text-danger">
          {problem}
        </p>
      ) : null}
      <Button size="lg" onClick={submit} disabled={soldOut || adding} aria-busy={adding || undefined} className="w-full">
        {soldOut ? (
          "Sold out"
        ) : adding ? (
          "Adding"
        ) : added ? (
          <>
            <Check aria-hidden="true" className="size-5" /> Added to cart
          </>
        ) : (
          <>
            <ShoppingBag aria-hidden="true" className="size-5" /> Add to cart
          </>
        )}
      </Button>
    </div>
  );
}
