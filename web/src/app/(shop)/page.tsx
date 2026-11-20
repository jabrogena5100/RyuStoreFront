import { CategoryGrid } from "@/components/CategoryGrid";
import { HeroFilmstrip } from "@/components/HeroFilmstrip";
import { ProductRow } from "@/components/ProductRow";
import { QuickFinder } from "@/components/QuickFinder";
import { ValueStrip } from "@/components/ValueStrip";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";
import { searchProducts } from "@/lib/api";
import { OCCASIONS } from "@/lib/catalog";
import { toSearch } from "@/lib/listing";

/** Always rendered per request: the build must not need the API, and the data is cached for 30 s by the fetch layer. */
export const dynamic = "force-dynamic";

export default async function Home() {
  // The API's cheapest-first page of one gives each tile its count, its "from" price and a picture; the rows are plain searches.
  // Each tile: the cheapest product gives the count and the "from" price; the newest gives the picture (the cheapest is
  // often an odd fit for the occasion, a flat under "After dark").
  const [tiles, tileImages, newest, under150, finder] = await Promise.all([
    Promise.all(OCCASIONS.map((o) => searchProducts({ category: o.categories.join(","), categoryExact: true, sort: "price_asc", pageSize: 1 }))),
    Promise.all(OCCASIONS.map((o) => searchProducts({ category: o.categories.join(","), categoryExact: true, sort: "newest", pageSize: 1 }))),
    searchProducts({ sort: "newest", pageSize: 8 }),
    searchProducts(toSearch({ price: "u150", sort: "newest" }, { page: "1" })).then((r) => r.items),
    searchProducts({ inStock: true, pageSize: 1 }),
  ]);
  return (
    <Container className="pb-4">
      <HeroFilmstrip products={newest.items} total={finder.total} />

      <Section id="categories" index="01" title="Shop by occasion" href="/products" hrefLabel="All shoes">
        <CategoryGrid tiles={OCCASIONS.map((o, i) => ({ slug: o.slug, total: tiles[i].total, cheapest: tiles[i].items[0], picture: tileImages[i].items[0] }))} />
      </Section>

      <div className="mt-12 sm:mt-16">
        <QuickFinder initialCount={finder.total} />
      </div>

      <Section id="under-150" index="02" title="Under $150" href="/products?price=u150">
        <ProductRow products={under150} label="Under $150" />
      </Section>

      <Section id="value" index="03" title="How the shop works">
        <ValueStrip />
      </Section>
    </Container>
  );
}
