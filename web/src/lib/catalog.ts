/*
 * Design-phase catalogue. The shapes mirror the API contract (ProductSummary and VariantView in
 * src/main/java/.../catalog) and the vocabulary comes from the backend's demo seeder: invented brands,
 * its eight categories, its materials and colours. When the frontend is wired to the real API this file
 * is replaced by fetch calls; components keep the same types.
 */

export type Gender = "men" | "women" | "unisex";

export interface ProductSummary {
  id: number;
  slug: string;
  name: string;
  brand: string;
  gender: Gender;
  categorySlug: string;
  categoryName: string;
  minPriceMinor: number;
  currency: "USD";
  inStock: boolean;
  colour: string;
}

export interface Variant {
  sku: string;
  sizeLabel: string;
  colour: string;
  priceMinor: number;
  currency: "USD";
  available: boolean;
}

export interface Product extends ProductSummary {
  description: string;
  material: string;
  details: string[];
  variants: Variant[];
}

export const CATEGORY_NAMES: Record<string, string> = {
  sneakers: "Sneakers",
  "chunky-sneakers": "Chunky sneakers",
  running: "Running",
  boots: "Boots",
  heels: "Heels",
  loafers: "Loafers",
  oxfords: "Oxfords and derbies",
  sandals: "Sandals",
};

/** Browse by occasion: how people arrive. Each maps to API categories. */
export const OCCASIONS = [
  { slug: "everyday", label: "Everyday sneakers", blurb: "Clean, low and easy", categories: ["sneakers"] },
  { slug: "street", label: "Street", blurb: "Chunky soles, big attitude", categories: ["chunky-sneakers"] },
  { slug: "race", label: "Race day", blurb: "Carbon-plate runners", categories: ["running"] },
  { slug: "night", label: "After dark", blurb: "Heels, loafers, oxfords", categories: ["heels", "loafers", "oxfords"] },
  { slug: "boots", label: "Boots", blurb: "Chelsea, combat, lace-up", categories: ["boots"] },
] as const;

export type OccasionSlug = (typeof OCCASIONS)[number]["slug"];

const WHITES = new Set(["white", "off-white", "cream", "ivory"]);

const SIZES: Record<Gender, string[]> = {
  women: ["5", "5.5", "6", "6.5", "7", "7.5", "8", "8.5", "9", "9.5", "10", "11"],
  men: ["7", "7.5", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12", "13"],
  unisex: ["6", "7", "8", "9", "10", "11", "12"],
};

interface Seed {
  brand: string;
  code: string;
  model: string;
  type: string;
  category: keyof typeof CATEGORY_NAMES;
  gender: Gender;
  dollars: number;
  colour: string;
  material: string;
  blurb: string;
  details: string[];
  soldOut?: boolean;
}

const SEEDS: Seed[] = [
  { brand: "Plain White Co.", code: "PW", model: "Aurel", type: "Court Sneaker", category: "sneakers", gender: "unisex", dollars: 145, colour: "white", material: "full-grain white leather", blurb: "A court shoe with nothing on it but the stitching.", details: ["Stitched cupsole, not glued.", "Wipes clean with a damp cloth.", "Replaceable insole."] },
  { brand: "Halden", code: "HA", model: "Calder", type: "Low Sneaker", category: "sneakers", gender: "men", dollars: 125, colour: "off-white", material: "pebbled calf leather", blurb: "The low sneaker, cut a little narrower.", details: ["Unlined heel for a quiet break-in.", "Stitched cupsole.", "Pebbled calf leather that hides creases."] },
  { brand: "Plain White Co.", code: "PW", model: "Isla", type: "Tennis Shoe", category: "sneakers", gender: "women", dollars: 110, colour: "white", material: "full-grain white leather", blurb: "Tennis-court proportions, softened for the street.", details: ["Wipes clean with a damp cloth.", "Replaceable insole.", "Unlined heel."] },
  { brand: "Halden", code: "HA", model: "Wren", type: "Minimal Sneaker", category: "sneakers", gender: "unisex", dollars: 165, colour: "stone", material: "brushed suede", blurb: "Brushed suede in a colour that goes with everything.", details: ["Stitched cupsole, not glued.", "Replaceable insole.", "Suede protector recommended."] },
  { brand: "Slab & Sole", code: "SS", model: "Dorian", type: "Platform Sneaker", category: "chunky-sneakers", gender: "women", dollars: 155, colour: "cream", material: "leather with a layered foam midsole", blurb: "Height without weight.", details: ["A 45 mm stacked sole adds height without weight.", "Padded tongue and collar.", "Reflective heel tab."] },
  { brand: "Slab & Sole", code: "SS", model: "Rhea", type: "Dad Sneaker", category: "chunky-sneakers", gender: "unisex", dollars: 170, colour: "grey", material: "mesh and suede overlays", blurb: "The big-soled classic, in a quiet grey.", details: ["Wide toe box with a sculpted heel counter.", "Padded tongue and collar.", "Reflective heel tab."] },
  { brand: "Slab & Sole", code: "SS", model: "Garnet", type: "Chunky Trainer", category: "chunky-sneakers", gender: "men", dollars: 185, colour: "black", material: "nubuck and ripstop", blurb: "All-black, all sole.", details: ["A 45 mm stacked sole adds height without weight.", "Wide toe box.", "Nubuck and ripstop upper."] },
  { brand: "Kestrel Run", code: "KR", model: "Vale", type: "Carbon Racer", category: "running", gender: "unisex", dollars: 265, colour: "black", material: "engineered knit", blurb: "Built for the last ten kilometres of a race.", details: ["Carbon-fibre plate for a stiff, springy toe-off.", "8 mm heel-to-toe drop.", "Foam midsole tuned for race day."] },
  { brand: "Tarn", code: "TA", model: "Nico", type: "Road Trainer", category: "running", gender: "men", dollars: 150, colour: "slate", material: "recycled mesh", blurb: "The everyday road shoe.", details: ["8 mm heel-to-toe drop.", "Gusseted tongue keeps debris out.", "Recycled mesh upper."] },
  { brand: "Kestrel Run", code: "KR", model: "Sela", type: "Tempo Shoe", category: "running", gender: "women", dollars: 180, colour: "coral", material: "lightweight woven upper", blurb: "Quick enough for tempo days, soft enough for the rest.", details: ["Foam midsole tuned for tempo runs.", "Gusseted tongue.", "Lightweight woven upper."] },
  { brand: "Ostrava Atelier", code: "OA", model: "Marais", type: "Chelsea Boot", category: "boots", gender: "women", dollars: 390, colour: "black", material: "polished calfskin", blurb: "A sleek Chelsea that goes from the office to the late train.", details: ["Goodyear-welted so it can be resoled.", "Leather-lined with a cushioned footbed.", "Pull tabs front and back."] },
  { brand: "Halden", code: "HA", model: "Harlan", type: "Combat Boot", category: "boots", gender: "women", dollars: 240, colour: "black", material: "oiled leather", blurb: "A boot with a heavy tread and a lighter step.", details: ["Rubber lug sole for wet pavement.", "Leather-lined with a cushioned footbed.", "Pull tabs front and back."] },
  { brand: "Tarn", code: "TA", model: "Rowan", type: "Lace-Up Boot", category: "boots", gender: "men", dollars: 285, colour: "chestnut", material: "waxed suede", blurb: "Waxed suede that darkens the longer you wear it.", details: ["Goodyear-welted so it can be resoled.", "Rubber lug sole for wet pavement.", "Waxed suede upper."] },
  { brand: "Ostrava Atelier", code: "OA", model: "Ione", type: "Ankle Boot", category: "boots", gender: "women", dollars: 345, colour: "oxblood", material: "polished calfskin", blurb: "A deep red you will keep reaching for.", details: ["Leather-lined with a cushioned footbed.", "Pull tabs front and back.", "Goodyear-welted."] },
  { brand: "Maison Verre", code: "MV", model: "Vesper", type: "Stiletto Pump", category: "heels", gender: "women", dollars: 890, colour: "black", material: "Italian nappa leather", blurb: "The evening pump, made on a narrow last.", details: ["Hand-lacquered leather outsole.", "Padded insole with a non-slip heel grip.", "Comes with a dust bag and spare heel tips."] },
  { brand: "Noir Meridian", code: "NM", model: "Alder", type: "Slingback", category: "heels", gender: "women", dollars: 720, colour: "ivory", material: "patent calfskin", blurb: "A slingback with a quiet shine.", details: ["Padded insole with a non-slip heel grip.", "Made on a narrow last; size up for a wider foot.", "Comes with a dust bag."], soldOut: true },
  { brand: "Ostrava Atelier", code: "OA", model: "Soren", type: "Penny Loafer", category: "loafers", gender: "men", dollars: 420, colour: "burgundy", material: "burnished calfskin", blurb: "A penny loafer in a deep burgundy.", details: ["Blake-stitched with a slim leather sole.", "Unlined for a softer fit.", "Rubber half-sole fitted at the factory."] },
  { brand: "Noir Meridian", code: "NM", model: "Faro", type: "Cap-Toe Oxford", category: "oxfords", gender: "men", dollars: 495, colour: "black", material: "box calf", blurb: "For the night out that starts with a dinner.", details: ["Leather sole, resoleable.", "Closed lacing for a dressier line.", "Padded collar, lined in calf leather."] },
  { brand: "Slab & Sole", code: "SS", model: "Kai", type: "Slide", category: "sandals", gender: "unisex", dollars: 85, colour: "sage", material: "moulded EVA", blurb: "The post-run, post-beach slide.", details: ["Contoured footbed.", "Dries quickly after a swim.", "Moulded EVA."] },
];

function build(seed: Seed, index: number): Product {
  const id = index + 1;
  const slug = `${seed.model}-${seed.type}`.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const sizes = SIZES[seed.gender];
  const colourCode = seed.colour.replace(/[^a-z]/g, "").slice(0, 3).toUpperCase();
  const variants: Variant[] = sizes.map((size, i) => ({
    sku: `${seed.code}-${String(id).padStart(5, "0")}-${colourCode}-${size}`,
    sizeLabel: size,
    colour: seed.colour,
    priceMinor: seed.dollars * 100,
    currency: "USD",
    // Deterministic gaps so the size picker shows sold-out sizes; a sold-out product has none.
    available: !seed.soldOut && (id * 31 + i * 17) % 7 !== 0 && (id * 13 + i * 3) % 5 !== 1,
  }));
  return {
    id,
    slug,
    name: `${seed.model} ${seed.type}`,
    brand: seed.brand,
    gender: seed.gender,
    categorySlug: seed.category,
    categoryName: CATEGORY_NAMES[seed.category],
    minPriceMinor: seed.dollars * 100,
    currency: "USD",
    inStock: variants.some((v) => v.available),
    colour: seed.colour,
    description: seed.blurb,
    material: seed.material,
    details: seed.details,
    variants,
  };
}

export const PRODUCTS: Product[] = SEEDS.map(build);

export function getProduct(slug: string): Product | undefined {
  return PRODUCTS.find((p) => p.slug === slug);
}

export function formatPrice(minor: number, currency: string = "USD"): string {
  const whole = minor % 100 === 0;
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: whole ? 0 : 2,
    maximumFractionDigits: whole ? 0 : 2,
  }).format(minor / 100);
}

export function occasionOf(p: ProductSummary) {
  return OCCASIONS.find((o) => (o.categories as readonly string[]).includes(p.categorySlug));
}

export type SortKey = "featured" | "price-asc" | "price-desc" | "newest";

export interface ListingQuery {
  q?: string;
  occasion?: string;
  gender?: string;
  size?: string;
  colour?: string;
  stock?: string;
  price?: string;
  sort?: string;
}

/** Price bands for the listing filter; the bounds are in minor units (cents). */
export const PRICE_BANDS = [
  { value: "u150", label: "Under $150", test: (m: number) => m < 15000 },
  { value: "150-300", label: "$150 to $300", test: (m: number) => m >= 15000 && m <= 30000 },
  { value: "o300", label: "Over $300", test: (m: number) => m > 30000 },
] as const;

export function filterProducts(q: ListingQuery): Product[] {
  const occasion = OCCASIONS.find((o) => o.slug === q.occasion);
  let list = PRODUCTS.filter((p) => {
    if (occasion && !(occasion.categories as readonly string[]).includes(p.categorySlug)) return false;
    if (q.gender && p.gender !== q.gender && p.gender !== "unisex") return false;
    if (q.colour === "white" && !WHITES.has(p.colour)) return false;
    if (q.size && !p.variants.some((v) => v.sizeLabel === q.size && v.available)) return false;
    if (q.stock === "1" && !p.inStock) return false;
    const band = PRICE_BANDS.find((b) => b.value === q.price);
    if (band && !band.test(p.minPriceMinor)) return false;
    if (q.q) {
      const hay = `${p.name} ${p.brand} ${p.categoryName} ${p.colour} ${p.material}`.toLowerCase();
      if (!q.q.toLowerCase().split(/\s+/).filter(Boolean).every((w) => hay.includes(w))) return false;
    }
    return true;
  });
  switch (q.sort as SortKey | undefined) {
    case "price-asc":
      list = [...list].sort((a, b) => a.minPriceMinor - b.minPriceMinor);
      break;
    case "price-desc":
      list = [...list].sort((a, b) => b.minPriceMinor - a.minPriceMinor);
      break;
    case "newest":
      list = [...list].sort((a, b) => b.id - a.id);
      break;
  }
  return list;
}

export const ALL_SIZES = Array.from(new Set(Object.values(SIZES).flat())).sort((a, b) => parseFloat(a) - parseFloat(b));

/** Flat shipping, in minor units: the value the API is configured with (storefront.checkout.shipping-flat-minor). */
export const SHIPPING_FLAT_MINOR = 800;

/** Lines used by design-review URLs (cart?state=filled): one available size from three different products. */
export function demoCartLines() {
  return [0, 7, 10].map((i) => {
    const p = PRODUCTS[i];
    const v = p.variants.find((x) => x.available) ?? p.variants[0];
    return {
      sku: v.sku,
      slug: p.slug,
      name: p.name,
      brand: p.brand,
      categorySlug: p.categorySlug,
      sizeLabel: v.sizeLabel,
      colour: v.colour,
      priceMinor: v.priceMinor,
      qty: i === 0 ? 2 : 1,
    };
  });
}

/** How many pairs a filter option would show: the current filters with that one option applied. Drives the counts beside each option. */
export function countWith(q: ListingQuery, patch: Partial<ListingQuery>): number {
  return filterProducts({ ...q, ...patch, sort: undefined }).length;
}
