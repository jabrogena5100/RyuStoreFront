import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { ProductCard } from "@/components/ProductCard";
import { QuickFinder } from "@/components/QuickFinder";
import { Reveal } from "@/components/ui/Reveal";
import { OCCASIONS, PRODUCTS, formatPrice } from "@/lib/catalog";

const SPAN: Record<string, string> = {
  everyday: "lg:col-span-6 lg:row-span-2 lg:min-h-[26rem]",
  street: "lg:col-span-6",
  race: "lg:col-span-3",
  night: "lg:col-span-3",
  boots: "lg:col-span-12",
};

const NUM: Record<string, string> = {
  everyday: "text-[clamp(10rem,21vw,19rem)]",
  street: "text-[clamp(6rem,11vw,10rem)]",
  race: "text-[clamp(5rem,8vw,7.5rem)]",
  night: "text-[clamp(5rem,8vw,7.5rem)]",
  boots: "text-[clamp(6rem,11vw,9rem)]",
};

function OccasionTile({ slug }: { slug: (typeof OCCASIONS)[number]["slug"] }) {
  const o = OCCASIONS.find((x) => x.slug === slug)!;
  const items = PRODUCTS.filter((p) => (o.categories as readonly string[]).includes(p.categorySlug));
  const from = Math.min(...items.map((p) => p.minPriceMinor));
  return (
    <Link
      href={`/products?occasion=${o.slug}`}
      className={`group relative flex min-h-48 flex-col justify-between overflow-hidden rounded-lg border border-line bg-surface-1 p-5 transition-colors duration-(--duration-base) hover:border-line-strong sm:p-6 ${SPAN[o.slug]}`}
    >
      {/* Generated content, not text: pure decoration that no assistive tech or contrast checker should read. */}
      <span
        aria-hidden="true"
        data-n={String(items.length).padStart(2, "0")}
        className={`pointer-events-none absolute -bottom-[0.2em] -right-[0.03em] select-none font-semibold leading-none tracking-tighter text-veil after:content-[attr(data-n)] transition-transform duration-(--duration-slower) ease-emphasized [@media(hover:hover)]:group-hover:-translate-y-2 ${NUM[o.slug]}`}
      />
      <div className="relative flex items-start justify-between gap-3">
        <div>
          <h3 className="text-h2 font-semibold text-fg-strong">{o.label}</h3>
          <p className="mt-1 text-small text-fg-muted">{o.blurb}</p>
        </div>
        <span className="grid size-11 shrink-0 place-items-center rounded-pill bg-surface-3 text-fg transition-colors duration-(--duration-base) group-hover:bg-accent group-hover:text-fg-strong">
          <ArrowRight aria-hidden="true" className="size-5" />
          <span className="sr-only">Shop {o.label}</span>
        </span>
      </div>
      <p className="tabular relative text-small text-fg-muted">
        {items.length} styles · from <span className="text-fg">{formatPrice(from)}</span>
      </p>
    </Link>
  );
}

export default function Home() {
  const newest = [...PRODUCTS].sort((a, b) => b.id - a.id).slice(0, 8);
  return (
    <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
      <section className="grid items-center gap-8 py-10 sm:py-14 lg:grid-cols-[1.05fr_1fr] lg:gap-14 lg:py-20">
        <div className="max-w-xl">
          <p className="text-caption font-medium uppercase text-accent-text">Sneakers · Runners · Boots · Evening</p>
          <h1 className="mt-4 text-display font-semibold text-fg-strong">Find the right pair, fast.</h1>
          <p className="mt-5 max-w-md text-body text-fg-muted">
            Say who it is for and what it is for. We show what is in stock in your size, and you are at checkout in a few taps.
          </p>
        </div>
        <Reveal>
          <QuickFinder />
        </Reveal>
      </section>

      <section aria-labelledby="occasions" className="py-6">
        <div className="mb-5 flex items-end justify-between">
          <h2 id="occasions" className="text-h1 font-semibold text-fg-strong">
            Shop by occasion
          </h2>
          <Link href="/products" className="py-3 text-small font-medium text-accent-text hover:underline">
            All shoes
          </Link>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-12">
          {OCCASIONS.map((o, i) => (
            <Reveal key={o.slug} delay={i * 0.05} className={`flex flex-col ${SPAN[o.slug].replace("lg:min-h-[26rem]", "")} ${o.slug === "boots" ? "sm:col-span-2" : ""}`}>
              <OccasionTile slug={o.slug} />
            </Reveal>
          ))}
        </div>
      </section>

      <section aria-labelledby="new" className="py-12">
        <h2 id="new" className="mb-5 text-h1 font-semibold text-fg-strong">
          New in
        </h2>
        <ul className="no-scrollbar -mx-4 flex snap-x snap-mandatory scroll-px-4 gap-4 overflow-x-auto px-4 pb-2 sm:-mx-6 sm:scroll-px-6 sm:px-6 lg:mx-0 lg:scroll-px-0 lg:px-0">
          {newest.map((p) => (
            <li key={p.id} className="w-[68%] shrink-0 snap-start sm:w-[36%] lg:w-[calc(25%-0.75rem)]">
              <ProductCard product={p} />
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
