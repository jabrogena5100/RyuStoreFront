"use client";

import clsx from "clsx";
import { useState } from "react";
import { ProductImage } from "@/components/ProductImage";
import type { ProductSummary } from "@/lib/catalog";

const VIEWS = [0, 1, 2, 3] as const;
const NAMES = ["Image 1", "Image 2", "Image 3", "Image 4"];

export function Gallery({ product }: { product: Pick<ProductSummary, "brand" | "name" | "categorySlug"> }) {
  const [view, setView] = useState<(typeof VIEWS)[number]>(0);
  return (
    <div className="flex flex-col gap-3 lg:flex-row-reverse">
      <div className="relative aspect-[4/5] flex-1 overflow-hidden rounded-lg border border-line">
        <div className="absolute inset-0">
          <ProductImage product={product} view={view} className="aspect-auto! h-full" />
        </div>
      </div>
      <div role="group" aria-label="Product views" className="flex gap-2 lg:w-20 lg:flex-col">
        {VIEWS.map((v) => (
          <button
            key={v}
            type="button"
            aria-label={`${NAMES[v]} of 4`}
            aria-pressed={view === v}
            onClick={() => setView(v)}
            className={clsx(
              "w-full overflow-hidden rounded-md border transition-colors duration-(--duration-base)",
              view === v ? "border-accent-text" : "border-line hover:border-line-strong",
            )}
          >
            <ProductImage product={product} view={v} label={false} decorative />
          </button>
        ))}
      </div>
    </div>
  );
}
