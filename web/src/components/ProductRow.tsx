"use client";

import clsx from "clsx";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useRef } from "react";
import { ProductCard } from "@/components/ProductCard";
import type { ProductSummary } from "@/lib/catalog";

/** A horizontal row of product cards: native scroll-snap (swipe on touch), plus previous and next buttons on wide screens when it overflows. */
export function ProductRow({ products, label }: { products: ProductSummary[]; label: string }) {
  const list = useRef<HTMLUListElement>(null);
  function scroll(dir: 1 | -1) {
    const el = list.current;
    if (!el) return;
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    el.scrollBy({ left: dir * el.clientWidth * 0.9, behavior: reduce ? "auto" : "smooth" });
  }
  const arrows = products.length > 4;
  const btn =
    "absolute top-[calc(50%-3.5rem)] z-10 hidden size-11 place-items-center rounded-pill border border-line-strong bg-surface-2 text-fg transition-colors duration-(--duration-base) hover:bg-surface-3 lg:grid";
  return (
    <div className="relative">
      <ul
        ref={list}
        aria-label={label}
        className="no-scrollbar -mx-4 flex snap-x snap-mandatory scroll-px-4 gap-4 overflow-x-auto px-4 sm:-mx-6 sm:scroll-px-6 sm:px-6 lg:mx-0 lg:scroll-px-0 lg:px-0"
      >
        {products.map((p) => (
          <li key={p.id} className={clsx("w-[62%] shrink-0 snap-start sm:w-[34%] md:w-[26%]", "lg:w-[calc((100%-3rem)/4)]")}>
            <ProductCard product={p} />
          </li>
        ))}
      </ul>
      {arrows ? (
        <>
          <button type="button" aria-label={`Scroll ${label} back`} onClick={() => scroll(-1)} className={clsx(btn, "-left-5")}>
            <ChevronLeft aria-hidden="true" className="size-5" />
          </button>
          <button type="button" aria-label={`Scroll ${label} forward`} onClick={() => scroll(1)} className={clsx(btn, "-right-5")}>
            <ChevronRight aria-hidden="true" className="size-5" />
          </button>
        </>
      ) : null}
    </div>
  );
}
