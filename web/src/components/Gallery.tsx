"use client";

import clsx from "clsx";
import { useState, type KeyboardEvent } from "react";
import { ProductImage } from "@/components/ProductImage";
import type { ProductSummary } from "@/lib/catalog";

const VIEWS = [0, 1, 2, 3] as const;
type View = (typeof VIEWS)[number];

/**
 * Product gallery. Pattern from the 21st.dev shortlist ("Thumbnail image viewer" by ibrandify: a counter, a thumbnail strip
 * and arrow-key navigation). Adapted: the image swaps with a 180 ms opacity fade (removed under reduced motion), thumbnails
 * are a radio-like group of buttons, Left/Right arrows move between them. Zoom arrives with the real photos (phase 4).
 */
export function Gallery({ product }: { product: Pick<ProductSummary, "brand" | "name" | "categorySlug"> }) {
  const [view, setView] = useState<View>(0);
  function onKey(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    e.preventDefault();
    const next = ((view + (e.key === "ArrowRight" ? 1 : -1) + VIEWS.length) % VIEWS.length) as View;
    setView(next);
    (e.currentTarget.querySelectorAll("button")[next] as HTMLButtonElement | undefined)?.focus();
  }
  return (
    <div className="flex flex-col gap-3">
      <div className="relative aspect-[4/5] overflow-hidden rounded-md bg-surface-1">
        {VIEWS.map((v) => (
          <div key={v} aria-hidden={view !== v} className={clsx("absolute inset-0 transition-opacity duration-(--duration-base) ease-standard", view === v ? "opacity-100" : "opacity-0")}>
            <ProductImage product={product} view={v} label={false} decorative={view !== v} className="aspect-auto! h-full" />
          </div>
        ))}
        <p className="absolute right-3 top-3 rounded-sm bg-canvas/80 px-2 py-1 font-mono text-caption text-fg">
          {String(view + 1).padStart(2, "0")} <span className="text-fg-muted">/ {String(VIEWS.length).padStart(2, "0")}</span>
        </p>
      </div>
      <div role="group" aria-label="Product views (left and right arrows move)" onKeyDown={onKey} className="grid grid-cols-4 gap-2">
        {VIEWS.map((v) => (
          <button
            key={v}
            type="button"
            aria-label={`View ${v + 1} of ${VIEWS.length}`}
            aria-pressed={view === v}
            tabIndex={view === v ? 0 : -1}
            onClick={() => setView(v)}
            className={clsx(
              "overflow-hidden rounded-sm border-2 transition-opacity duration-(--duration-base)",
              view === v ? "border-accent" : "border-transparent opacity-70 hover:opacity-100",
            )}
          >
            <ProductImage product={product} view={v} label={false} decorative />
          </button>
        ))}
      </div>
    </div>
  );
}
