import type { Metadata } from "next";
import Link from "next/link";
import { ChevronDown } from "lucide-react";
import { ProductCard } from "@/components/ProductCard";
import { SortSelect } from "@/components/SortSelect";
import { ButtonLink } from "@/components/ui/Button";
import { ChipLink } from "@/components/ui/Chip";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/StatePanel";
import { ALL_SIZES, OCCASIONS, filterProducts, type ListingQuery } from "@/lib/catalog";

type Raw = Record<string, string | string[] | undefined>;
const first = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);

function clean(raw: Raw): ListingQuery & { state?: string } {
  return {
    q: first(raw.q)?.slice(0, 100),
    occasion: first(raw.occasion),
    gender: first(raw.gender),
    size: first(raw.size),
    colour: first(raw.colour),
    stock: first(raw.stock),
    sort: first(raw.sort),
    state: first(raw.state),
  };
}

function href(q: ListingQuery, patch: Partial<ListingQuery>) {
  const next: Record<string, string | undefined> = { ...q, ...patch };
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(next)) if (v) p.set(k, v);
  const s = p.toString();
  return s ? `/products?${s}` : "/products";
}

export async function generateMetadata({ searchParams }: { searchParams: Promise<Raw> }): Promise<Metadata> {
  const q = clean(await searchParams);
  const o = OCCASIONS.find((x) => x.slug === q.occasion);
  return { title: q.q ? `Search: ${q.q}` : (o?.label ?? "All shoes") };
}

export default async function Listing({ searchParams }: { searchParams: Promise<Raw> }) {
  const query = clean(await searchParams);
  const { state, ...q } = query;
  const occasion = OCCASIONS.find((o) => o.slug === q.occasion);
  const results = filterProducts(q);
  const filtered = Boolean(q.occasion || q.gender || q.size || q.colour || q.stock || q.q);
  const baseNoSort = href({ ...q, sort: undefined }, {});

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <nav aria-label="Breadcrumb" className="text-small text-fg-subtle">
        <Link href="/" className="hover:text-fg">Home</Link> <span aria-hidden="true">/</span>{" "}
        <span className="text-fg-muted">{occasion?.label ?? "All shoes"}</span>
      </nav>
      <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-h1 font-semibold text-fg-strong">{q.q ? `Results for "${q.q}"` : (occasion?.label ?? "All shoes")}</h1>
          {occasion ? <p className="mt-1 text-small text-fg-muted">{occasion.blurb}</p> : null}
        </div>
        <SortSelect base={baseNoSort} value={q.sort ?? "featured"} />
      </div>

      <div className="mt-6 flex flex-col gap-3" aria-label="Filters" role="group">
        <div className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:flex-wrap sm:px-0">
          <ChipLink href={href(q, { occasion: undefined })} selected={!q.occasion}>All</ChipLink>
          {OCCASIONS.map((o) => (
            <ChipLink key={o.slug} href={href(q, { occasion: q.occasion === o.slug ? undefined : o.slug })} selected={q.occasion === o.slug}>
              {o.label}
            </ChipLink>
          ))}
        </div>
        <div className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:flex-wrap sm:px-0">
          <ChipLink href={href(q, { gender: q.gender === "women" ? undefined : "women" })} selected={q.gender === "women"}>Women</ChipLink>
          <ChipLink href={href(q, { gender: q.gender === "men" ? undefined : "men" })} selected={q.gender === "men"}>Men</ChipLink>
          <ChipLink href={href(q, { colour: q.colour === "white" ? undefined : "white" })} selected={q.colour === "white"}>White</ChipLink>
          <ChipLink href={href(q, { stock: q.stock === "1" ? undefined : "1" })} selected={q.stock === "1"}>In stock</ChipLink>
        </div>
        <details className="group rounded-lg border border-line bg-surface-1" open={Boolean(q.size)}>
          <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between px-4 text-small font-medium text-fg">
            <span>Size{q.size ? `: US ${q.size}` : ""}</span>
            <ChevronDown aria-hidden="true" className="size-4 text-fg-subtle transition-transform duration-(--duration-base) group-open:rotate-180" />
          </summary>
          <div className="flex flex-wrap gap-2 border-t border-line p-4">
            {ALL_SIZES.map((s) => (
              <ChipLink key={s} href={href(q, { size: q.size === s ? undefined : s })} selected={q.size === s} className="tabular min-w-12 justify-center px-3">
                {s}
              </ChipLink>
            ))}
          </div>
        </details>
      </div>

      <p className="mt-6 text-small text-fg-muted" role="status" aria-live="polite">
        {state === "loading" ? "Loading shoes" : state === "error" ? "" : `${results.length} ${results.length === 1 ? "pair" : "pairs"}`}
        {filtered && state !== "loading" && state !== "error" ? (
          <>
            {" · "}
            <Link href="/products" className="text-accent-text hover:underline">Clear filters</Link>
          </>
        ) : null}
      </p>

      <div className="mt-4">
        {state === "loading" ? (
          <ul aria-busy="true" className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: 8 }, (_, i) => (
              <li key={i} className="flex flex-col gap-3">
                <Skeleton className="aspect-[4/5] w-full rounded-lg" />
                <Skeleton className="h-3 w-16" />
                <Skeleton className="h-5 w-3/4" />
              </li>
            ))}
          </ul>
        ) : state === "error" ? (
          <ErrorState title="We could not load the shoes" action={<ButtonLink href="/products">Try again</ButtonLink>}>
            Something went wrong on our side. Nothing is lost; try again in a moment.
          </ErrorState>
        ) : state === "empty" || results.length === 0 ? (
          <EmptyState title="No pairs match these filters" action={<ButtonLink href="/products" variant="secondary">Clear all filters</ButtonLink>}>
            Try another size or occasion, or remove a filter. Sizes sell out quickly.
          </EmptyState>
        ) : (
          <ul className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-4">
            {results.map((p, i) => (
              <li key={p.id}>
                <ProductCard product={p} priority={i < 4} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
