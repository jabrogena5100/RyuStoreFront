import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import clsx from "clsx";
import { ProductImage } from "@/components/ProductImage";
import { OCCASIONS, formatPrice, type ProductSummary } from "@/lib/catalog";

export type CategoryTileData = { slug: (typeof OCCASIONS)[number]["slug"]; total: number; cheapest: ProductSummary | undefined };

/**
 * Shop-by-occasion grid. Pattern from the 21st.dev shortlist ("Shop by Category Grid" by ziegfiroyt: image cards with a
 * count and a hover zoom). Adapted: the lead tile spans two columns and two rows on wide screens; the zoom is a 3 % scale
 * (transform only, 240 ms, removed under reduced motion); text sits on a dark scrim so it keeps contrast on any photo.
 */
export function CategoryGrid({ tiles }: { tiles: CategoryTileData[] }) {
  return (
    <ul className="grid grid-cols-2 gap-3 md:grid-cols-2 lg:grid-cols-4 lg:grid-rows-2">
      {tiles.map((t, i) => {
        const o = OCCASIONS.find((x) => x.slug === t.slug)!;
        const lead = i === 0;
        return (
          <li key={t.slug} className={clsx(lead ? "col-span-2 lg:row-span-2" : "col-span-1")}>
            <Link
              href={`/products?occasion=${o.slug}`}
              className={clsx("group relative block h-full overflow-hidden rounded-md bg-surface-1", lead ? "aspect-[4/3] lg:aspect-auto" : "aspect-[4/5] md:aspect-[4/3]")}
            >
              <div className="absolute inset-0 transition-transform duration-(--duration-slow) ease-standard group-hover:scale-[1.03] group-focus-visible:scale-[1.03]">
                {t.cheapest ? <ProductImage product={t.cheapest} label={false} decorative className="aspect-auto! h-full" /> : null}
              </div>
              <div aria-hidden="true" className="absolute inset-x-0 bottom-0 h-2/3 bg-gradient-to-t from-canvas via-canvas/70 to-transparent" />
              <ArrowUpRight aria-hidden="true" className="absolute right-3 top-3 size-5 text-fg-muted transition-colors duration-(--duration-base) group-hover:text-accent-text" />
              <div className="absolute inset-x-0 bottom-0 p-4 sm:p-5">
                <h3 className={clsx("font-bold tracking-tight text-fg-strong", lead ? "text-h1" : "text-h2")}>{o.label}</h3>
                {lead ? <p className="mt-1 max-w-xs text-small text-fg-muted">{o.blurb}</p> : null}
                <p className="mt-2 font-mono text-caption uppercase text-fg-muted">
                  {t.total} {t.total === 1 ? "style" : "styles"}
                  {t.cheapest ? ` · from ${formatPrice(t.cheapest.minPriceMinor)}` : ""}
                </p>
              </div>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
