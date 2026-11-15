import type { Metadata } from "next";
import Link from "next/link";
import { X } from "lucide-react";
import { FilterDrawer } from "@/components/FilterDrawer";
import { ListingFilters } from "@/components/ListingFilters";
import { ProductCard } from "@/components/ProductCard";
import { SortSelect } from "@/components/SortSelect";
import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/StatePanel";
import { OCCASIONS, filterProducts, type ListingQuery } from "@/lib/catalog";
import { activeFilters, listingHref } from "@/lib/listing";

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
    price: first(raw.price),
    sort: first(raw.sort),
    state: first(raw.state),
  };
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
  const active = activeFilters(q);
  const baseNoSort = listingHref({ ...q, sort: undefined });
  const shown = state === "loading" || state === "error" ? null : results.length;
  const countText = shown === null ? (state === "loading" ? "Loading shoes" : "") : `${shown} ${shown === 1 ? "pair" : "pairs"}`;
  const grid = "grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 xl:grid-cols-4";

  return (
    <Container className="py-8">
      <nav aria-label="Breadcrumb" className="text-small text-fg-subtle">
        <Link href="/" className="hover:text-fg">Home</Link> <span aria-hidden="true">/</span>{" "}
        <span className="text-fg-muted">{occasion?.label ?? "All shoes"}</span>
      </nav>
      <div className="mt-3">
        <h1 className="text-h1 font-semibold text-fg-strong">{q.q ? `Results for "${q.q}"` : (occasion?.label ?? "All shoes")}</h1>
        {occasion ? <p className="mt-1 text-small text-fg-muted">{occasion.blurb}</p> : null}
      </div>

      <div className="mt-8 grid gap-10 lg:grid-cols-[15rem_minmax(0,1fr)] lg:gap-12">
        <aside aria-label="Filter shoes" className="hidden lg:block">
          <ListingFilters q={q} />
        </aside>

        <div className="min-w-0">
          <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
            <FilterDrawer activeCount={active.length} resultLabel={shown === null ? "Show results" : `Show ${shown} ${shown === 1 ? "pair" : "pairs"}`}>
              <ListingFilters q={q} />
            </FilterDrawer>
            <p className="tabular order-last w-full text-small text-fg-muted lg:order-none lg:w-auto" role="status" aria-live="polite">{countText}</p>
            <SortSelect base={baseNoSort} value={q.sort ?? "featured"} />
          </div>

          {active.length > 0 ? (
            <ul aria-label="Active filters" className="mt-4 flex flex-wrap items-center gap-2">
              {active.map((f) => (
                <li key={f.key}>
                  <Link
                    href={listingHref(q, { [f.key]: undefined })}
                    aria-label={`Remove filter: ${f.label}`}
                    className="inline-flex min-h-11 items-center gap-2 rounded-pill border border-accent-line bg-accent-soft pl-4 pr-3 text-small font-medium text-accent-text transition-colors duration-(--duration-base) hover:bg-surface-3"
                  >
                    {f.label}
                    <X aria-hidden="true" className="size-4" />
                  </Link>
                </li>
              ))}
              <li>
                <Link href="/products" className="inline-flex min-h-11 items-center px-2 text-small text-fg-muted underline-offset-4 hover:text-fg hover:underline">
                  Clear all
                </Link>
              </li>
            </ul>
          ) : null}

          <div className="mt-6">
            {state === "loading" ? (
              <ul aria-busy="true" className={grid}>
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
              <ul className={grid}>
                {results.map((p, i) => (
                  <li key={p.id}>
                    <ProductCard product={p} priority={i < 4} />
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </Container>
  );
}
