"use client";

/*
 * Product gallery. Adapted from "Thumbnail image viewer" by ibrandify on 21st.dev
 * (https://21st.dev/@ibrandify/components/thumbnail-image-viewer), licence shown there: MIT. Kept from the original: the
 * "01 / 04" counter, previous and next buttons on the stage, thumbnails as a tablist with roving tabindex, and
 * Left/Right/Home/End keys. Changed: the stage is the product's 4:5 frame, the swap is a 180 ms opacity fade (removed under
 * reduced motion), no captions or location tags, no backdrop blur, design tokens. See design/COMPONENT-NOTES.md.
 */
import clsx from "clsx";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useId, useRef, useState, type KeyboardEvent } from "react";
import { ProductImage } from "@/components/ProductImage";
import type { ProductSummary } from "@/lib/catalog";

const VIEWS = [0, 1, 2, 3] as const;
type View = (typeof VIEWS)[number];
const pad = (n: number) => String(n).padStart(2, "0");

export function Gallery({ product }: { product: Pick<ProductSummary, "brand" | "name" | "categorySlug"> }) {
  const [active, setActive] = useState<View>(0);
  const id = useId();
  const tabs = useRef<HTMLDivElement>(null);
  const choose = (n: number) => setActive((((n % VIEWS.length) + VIEWS.length) % VIEWS.length) as View);

  function onKey(e: KeyboardEvent<HTMLDivElement>) {
    const map: Record<string, number> = { ArrowRight: active + 1, ArrowLeft: active - 1, Home: 0, End: VIEWS.length - 1 };
    if (!(e.key in map)) return;
    e.preventDefault();
    const next = (((map[e.key] % VIEWS.length) + VIEWS.length) % VIEWS.length) as View;
    setActive(next);
    tabs.current?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus();
  }

  const nav = "grid size-11 place-items-center rounded-pill border border-line-control bg-canvas/90 text-fg transition-colors duration-(--duration-base) hover:bg-surface-2";
  return (
    <div className="flex flex-col gap-3">
      <div id={`${id}-panel`} role="tabpanel" aria-labelledby={`${id}-tab-${active}`} className="relative aspect-[4/5] overflow-hidden rounded-md bg-surface-1">
        {VIEWS.map((v) => (
          <div key={v} aria-hidden={active !== v} className={clsx("absolute inset-0 transition-opacity duration-(--duration-base) ease-standard", active === v ? "opacity-100" : "opacity-0")}>
            <ProductImage product={product} view={v} label={false} decorative={active !== v} className="aspect-auto! h-full" />
          </div>
        ))}
        <p className="absolute right-3 top-3 rounded-sm bg-canvas/85 px-2 py-1 font-mono text-caption text-fg">
          {pad(active + 1)} <span className="text-fg-muted">/ {pad(VIEWS.length)}</span>
        </p>
        <div className="absolute bottom-3 right-3 flex gap-2">
          <button type="button" aria-label="Previous view" onClick={() => choose(active - 1)} className={nav}>
            <ChevronLeft aria-hidden="true" className="size-5" />
          </button>
          <button type="button" aria-label="Next view" onClick={() => choose(active + 1)} className={nav}>
            <ChevronRight aria-hidden="true" className="size-5" />
          </button>
        </div>
      </div>
      <div ref={tabs} role="tablist" aria-label="Product views" onKeyDown={onKey} className="grid grid-cols-4 gap-2">
        {VIEWS.map((v) => (
          <button
            key={v}
            type="button"
            role="tab"
            id={`${id}-tab-${v}`}
            aria-selected={active === v}
            aria-controls={`${id}-panel`}
            tabIndex={active === v ? 0 : -1}
            onClick={() => setActive(v)}
            className={clsx(
              "relative overflow-hidden rounded-sm border-2 transition-opacity duration-(--duration-base)",
              active === v ? "border-accent" : "border-transparent opacity-60 hover:opacity-100",
            )}
          >
            <ProductImage product={product} view={v} label={false} decorative />
            <span className="absolute bottom-1 left-1.5 font-mono text-caption text-fg">
              <span className="sr-only">View </span>
              {pad(v + 1)}
              <span className="sr-only"> of {pad(VIEWS.length)}</span>
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
