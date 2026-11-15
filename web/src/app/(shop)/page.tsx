import Link from "next/link";
import { ProductImage } from "@/components/ProductImage";
import { ProductRow } from "@/components/ProductRow";
import { QuickFinder } from "@/components/QuickFinder";
import { ValueStrip } from "@/components/ValueStrip";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";
import { searchProducts } from "@/lib/api";
import { OCCASIONS, formatPrice, type ProductSummary } from "@/lib/catalog";
import { toSearch } from "@/lib/listing";

/** Always rendered per request: the build must not need the API, and the data is cached for 30 s by the fetch layer. */
export const dynamic = "force-dynamic";

function CategoryTile({ slug, total, cheapest }: { slug: (typeof OCCASIONS)[number]["slug"]; total: number; cheapest: ProductSummary | undefined }) {
  const o = OCCASIONS.find((x) => x.slug === slug)!;
  return (
    <Link
      href={`/products?occasion=${o.slug}`}
      className="group flex h-full flex-col overflow-hidden rounded-lg border border-line bg-surface-1 transition-colors duration-(--duration-base) hover:border-line-strong"
    >
      {cheapest ? <ProductImage product={cheapest} label={false} decorative className="aspect-[4/3]!" /> : <div className="aspect-[4/3] bg-surface-2" />}
      <div className="flex flex-1 flex-col p-4">
        <h3 className="text-body font-semibold text-fg-strong">{o.label}</h3>
        <p className="mt-0.5 text-small text-fg-muted">{o.blurb}</p>
        <p className="tabular mt-auto pt-3 text-small text-fg-muted">
          {total} {total === 1 ? "style" : "styles"}
          {cheapest ? (
            <>
              {" "}· from <span className="text-fg">{formatPrice(cheapest.minPriceMinor)}</span>
            </>
          ) : null}
        </p>
      </div>
    </Link>
  );
}

export default async function Home() {
  // The API's cheapest-first page of one gives each tile its count, its "from" price and a picture; the rows are plain searches.
  const [tiles, newest, under150, finder] = await Promise.all([
    Promise.all(OCCASIONS.map((o) => searchProducts({ category: o.categories.join(","), sort: "price_asc", pageSize: 1 }))),
    searchProducts({ sort: "newest", pageSize: 8 }),
    searchProducts(toSearch({ price: "u150", sort: "price-asc" }, { page: "1" })).then((r) => r.items),
    searchProducts({ inStock: true, pageSize: 1 }),
  ]);
  return (
    <Container className="pt-8 pb-4 sm:pt-10">
      <section className="grid items-center gap-8 lg:grid-cols-[1.05fr_1fr] lg:gap-14">
        <div className="max-w-xl">
          <p className="text-caption font-medium uppercase text-accent-text">Sneakers · Runners · Boots · Evening</p>
          <h1 className="mt-4 text-display font-semibold text-fg-strong">Find the right pair, fast.</h1>
          <p className="mt-5 max-w-md text-body text-fg-muted">
            Say who it is for and what it is for. We show what is in stock in your size, and you are at checkout in a few taps.
          </p>
        </div>
        <QuickFinder initialCount={finder.total} />
      </section>

      <Section id="categories" title="Shop by occasion" href="/products" hrefLabel="All shoes">
        <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {OCCASIONS.map((o, i) => (
            <li key={o.slug} className={i === OCCASIONS.length - 1 ? "col-span-2 sm:col-span-1" : undefined}>
              <CategoryTile slug={o.slug} total={tiles[i].total} cheapest={tiles[i].items[0]} />
            </li>
          ))}
        </ul>
      </Section>

      <Section id="new" title="New in" href="/products?sort=newest">
        <ProductRow products={newest.items} label="New in" />
      </Section>

      <Section id="under-150" title="Under $150" href="/products?price=u150">
        <ProductRow products={under150} label="Under $150" />
      </Section>

      <Section id="value" title="How the shop works" className="mt-12 sm:mt-16">
        <ValueStrip />
      </Section>
    </Container>
  );
}
