import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { ProductImage } from "@/components/ProductImage";
import { ButtonLink } from "@/components/ui/Button";
import { Price } from "@/components/ui/Price";
import type { ProductSummary } from "@/lib/catalog";

/*
 * Home hero. Adapted from "Hero Carousel" by crafterui on 21st.dev (https://21st.dev/@crafterui/components/hero-carousel),
 * licence shown there: MIT. Kept from the original: an oversized headline sitting on a filmstrip whose frames share one top
 * edge, one lead frame larger than its neighbours, numbered frames in a mono label. Changed: the frames are the newest
 * catalogue items and plain links (no carousel state); no background re-grade, film grain, wheel capture, drag or autoplay;
 * a native scroll-snap row on phones; hover lifts a frame 4 px (transform only, 180 ms, removed under reduced motion);
 * design tokens. See design/COMPONENT-NOTES.md.
 */
export function HeroFilmstrip({ products, total }: { products: ProductSummary[]; total: number }) {
  const frames = products.slice(0, 6);
  return (
    <section aria-labelledby="hero-title" className="border-b border-line pb-10 sm:pb-14">
      <div className="grid gap-6 pt-8 sm:pt-12 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-end lg:gap-12">
        <div>
          <p className="font-mono text-caption uppercase text-fg-muted">
            <span className="text-accent-text">{total}</span> styles in stock · Sneakers · Runners · Boots · Evening
          </p>
          <h1 id="hero-title" className="mt-4 text-hero font-bold text-fg-strong">
            Every pair,
            <br />
            <span className="text-fg-muted">by the occasion.</span>
          </h1>
        </div>
        <div className="max-w-sm">
          <p className="text-body text-fg-muted">
            Pick the moment, tap your size, and you are at checkout in a minute. Only sizes in stock are shown.
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <ButtonLink href="/products" size="lg">
              Shop all shoes <ArrowRight aria-hidden="true" className="size-4" />
            </ButtonLink>
            <ButtonLink href="#finder" size="lg" variant="secondary">
              Find my size
            </ButtonLink>
          </div>
        </div>
      </div>

      <ol
        aria-label="New in"
        className="no-scrollbar -mx-4 mt-10 flex snap-x snap-mandatory scroll-px-4 items-start gap-3 overflow-x-auto px-4 sm:-mx-6 sm:scroll-px-6 sm:px-6 lg:mx-0 lg:grid lg:grid-cols-[minmax(0,2fr)_repeat(5,minmax(0,1fr))] lg:overflow-visible lg:px-0"
      >
        {frames.map((p, i) => (
          <li key={p.id} className={i === 0 ? "w-[78%] min-w-0 shrink-0 snap-start sm:w-[46%] lg:w-auto" : "w-[44%] min-w-0 shrink-0 snap-start sm:w-[26%] lg:w-auto"}>
            <Link href={`/products/${p.slug}`} className="group block rounded-md focus-visible:outline-offset-4">
              <div className="overflow-hidden rounded-md transition-transform duration-(--duration-base) ease-standard group-hover:-translate-y-1 group-focus-visible:-translate-y-1">
                <ProductImage
                  product={p}
                  image={p.image}
                  label={false}
                  decorative
                  priority={i === 0}
                  sizes={i === 0 ? "(min-width: 1024px) 30vw, (min-width: 640px) 46vw, 78vw" : "(min-width: 1024px) 14vw, (min-width: 640px) 26vw, 44vw"}
                />
              </div>
              <div className="mt-2.5 flex items-baseline justify-between gap-2">
                <p className="min-w-0 truncate text-small text-fg">
                  <span className="mr-2 font-mono text-caption text-fg-muted">{String(i + 1).padStart(2, "0")}</span>
                  {p.name}
                </p>
                <Price minor={p.minPriceMinor} currency={p.currency} className="shrink-0 text-small" />
              </div>
            </Link>
          </li>
        ))}
      </ol>
    </section>
  );
}
