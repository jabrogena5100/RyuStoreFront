import "server-only";
import { countProducts } from "@/lib/api";
import { ALL_SIZES, OCCASIONS, PRICE_BANDS, type ListingQuery } from "@/lib/catalog";
import { toSearch } from "@/lib/listing";

export interface FacetCounts {
  occasion: Record<string, number>;
  gender: Record<string, number>;
  price: Record<string, number>;
  size: Record<string, number>;
  stock: number;
}

/**
 * How many pairs each filter option would show, given the other filters. The API returns no facet counts, so this is one
 * small count query per option (about 25 per page view, run in parallel; the API caches nothing, Next caches each for 30 s).
 * A single counts endpoint is listed in docs/api/proposed-ui-needs.md; measure before building it.
 */
export async function facetCounts(q: ListingQuery): Promise<FacetCounts> {
  const one = (patch: Partial<ListingQuery>) => countProducts({ ...toSearch(q, { ...patch, page: "1" }) });
  const [occasion, gender, price, size, stock] = await Promise.all([
    Promise.all(OCCASIONS.map((o) => one({ occasion: o.slug }))),
    Promise.all(["women", "men"].map((g) => one({ gender: g }))),
    Promise.all(PRICE_BANDS.map((b) => one({ price: b.value }))),
    Promise.all(ALL_SIZES.map((s) => one({ size: s }))),
    one({ stock: "1" }),
  ]);
  return {
    occasion: Object.fromEntries(OCCASIONS.map((o, i) => [o.slug, occasion[i]])),
    gender: { women: gender[0], men: gender[1] },
    price: Object.fromEntries(PRICE_BANDS.map((b, i) => [b.value, price[i]])),
    size: Object.fromEntries(ALL_SIZES.map((s, i) => [s, size[i]])),
    stock,
  };
}
