import "server-only";
import type { Gender, ProductDetail, ProductSummary } from "@/lib/catalog";

/** Server-side base URL of the API (server components talk to it directly; the browser goes through the /api proxy). */
const ORIGIN = process.env.API_ORIGIN ?? "http://localhost:8080";

export interface PageResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface CategoryView {
  slug: string;
  name: string;
  parentSlug: string | null;
}

/** The one error body of the API (`{code, message, fields}`) or, for the catalogue's plain 400s, Spring's default body. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fields: Record<string, string> = {},
  ) {
    super(message);
  }
}

type Params = Record<string, string | number | boolean | undefined>;

async function get<T>(path: string, params: Params = {}, revalidate: number | false = false): Promise<T> {
  const url = new URL(`${ORIGIN}${path}`);
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== "") url.searchParams.set(k, String(v));
  let res: Response;
  try {
    // Catalogue data may be a few seconds old; product detail (stock) is never cached.
    res = await fetch(url, revalidate === false ? { cache: "no-store" } : { next: { revalidate } });
  } catch {
    throw new ApiError(0, "unreachable", "The API could not be reached.");
  }
  if (!res.ok) {
    let body: { code?: string; message?: string; fields?: Record<string, string> } = {};
    try {
      body = await res.json();
    } catch {
      /* not JSON */
    }
    throw new ApiError(res.status, body.code ?? `http_${res.status}`, body.message ?? res.statusText, body.fields);
  }
  return (await res.json()) as T;
}

export interface SearchParams {
  q?: string;
  category?: string;
  /** Match the named categories only, not their child categories (sneakers without chunky sneakers). */
  categoryExact?: boolean;
  gender?: Gender | string;
  minPrice?: number;
  maxPrice?: number;
  size?: string;
  inStock?: boolean;
  sort?: "relevance" | "newest" | "price_asc" | "price_desc";
  /** 0-based, as in the API. */
  page?: number;
  pageSize?: number;
}

/** GET /api/products. Lists are cached for 30 seconds so the filter counts (many small queries) stay cheap. */
export function searchProducts(p: SearchParams): Promise<PageResult<ProductSummary>> {
  return get("/api/products", { ...p, inStock: p.inStock ? true : undefined, categoryExact: p.categoryExact ? true : undefined }, 30);
}

/** Only the `total` of a search: one item per page keeps the response tiny. Used for the counts beside filter options. */
export async function countProducts(p: SearchParams): Promise<number> {
  return (await searchProducts({ ...p, sort: undefined, page: 0, pageSize: 1 })).total;
}

/** GET /api/products/{slug}; null on 404. Never cached: availability changes as people buy. */
export async function fetchProduct(slug: string): Promise<ProductDetail | null> {
  try {
    return await get<ProductDetail>(`/api/products/${encodeURIComponent(slug)}`);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

export function fetchCategories(): Promise<CategoryView[]> {
  return get("/api/categories", {}, 300);
}
