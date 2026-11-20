import type { Metadata } from "next";
import clsx from "clsx";
import Link from "next/link";
import { ChevronRight, Search } from "lucide-react";
import { Badge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/StatePanel";
import { PRODUCTS, formatPrice } from "@/lib/catalog";
import { stockOf } from "@/lib/orders";

export const metadata: Metadata = { title: "Products · Admin" };

/** Low stock means at most this many pairs across all sizes. A shop setting in real life; fixed here. */
const LOW = 12;
type Raw = { stock?: string; q?: string };

function summarise(p: (typeof PRODUCTS)[number]) {
  const v = stockOf(p);
  const units = v.reduce((n, x) => n + x.stock, 0);
  const sizes = v.filter((x) => x.stock > 0).length;
  const level = units === 0 ? "sold" : units <= LOW ? "low" : "ok";
  return { units, sizes, total: v.length, level } as const;
}

export default async function AdminProducts({ searchParams }: { searchParams: Promise<Raw> }) {
  const { stock, q } = await searchParams;
  const needle = q?.trim().toLowerCase();
  const all = PRODUCTS.filter((p) => !needle || `${p.name} ${p.brand} ${p.categoryName}`.toLowerCase().includes(needle)).map((p) => ({ p, s: summarise(p) }));
  const rows = all.filter(({ s }) => !stock || s.level === stock);
  const chip = "inline-flex min-h-11 items-center gap-2 rounded-pill border px-4 text-small font-medium whitespace-nowrap transition-colors duration-(--duration-base)";
  const on = "border-accent-line bg-accent-soft text-accent-text";
  const off = "border-line bg-surface-1 text-fg-muted hover:bg-surface-2 hover:text-fg";
  const href = (s?: string) => `/admin/products${s || q ? `?${new URLSearchParams({ ...(s ? { stock: s } : {}), ...(q ? { q } : {}) })}` : ""}`;

  return (
    <div>
      <h1 className="text-h1 font-semibold text-fg-strong">Products</h1>
      <form action="/admin/products" role="search" className="mt-5 flex max-w-xl">
        {stock ? <input type="hidden" name="stock" value={stock} /> : null}
        <label htmlFor="q" className="sr-only">Search products by name, brand or category</label>
        <input id="q" name="q" type="search" defaultValue={q} placeholder="Search by name, brand or category" className="min-h-11 min-w-0 flex-1 rounded-l-md border border-r-0 border-line-control bg-surface-2 px-4 text-small text-fg placeholder:text-fg-subtle" />
        <button type="submit" aria-label="Search" className="grid min-h-11 w-12 place-items-center rounded-r-md bg-accent text-on-accent transition-colors duration-(--duration-base) hover:bg-accent-hover">
          <Search aria-hidden="true" className="size-4" />
        </button>
      </form>
      <ul aria-label="Filter by stock" className="no-scrollbar -mx-4 mt-4 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:flex-wrap sm:px-0">
        {[
          { v: undefined, label: "All", n: all.length },
          { v: "low", label: `Low stock (${LOW} or fewer)`, n: all.filter(({ s }) => s.level === "low").length },
          { v: "sold", label: "Sold out", n: all.filter(({ s }) => s.level === "sold").length },
        ].map((f) => (
          <li key={f.label}>
            <Link href={href(f.v)} aria-current={stock === f.v || (!stock && !f.v) ? "true" : undefined} className={clsx(chip, stock === f.v || (!stock && !f.v) ? on : off)}>
              {f.label} <span className="tabular text-fg-subtle">{f.n}</span>
            </Link>
          </li>
        ))}
      </ul>

      <div className="mt-6">
        {rows.length === 0 ? (
          <EmptyState title="No products match" action={<ButtonLink href="/admin/products" variant="secondary">Clear filters</ButtonLink>}>
            Try another search or stock filter.
          </EmptyState>
        ) : (
          <>
            <div className="hidden overflow-hidden rounded-lg border border-line md:block">
              <table className="w-full text-left text-small">
                <caption className="sr-only">Products</caption>
                <thead className="bg-surface-1 text-caption uppercase text-fg-muted">
                  <tr>
                    <th scope="col" className="px-4 py-3 font-medium">Product</th>
                    <th scope="col" className="px-4 py-3 font-medium">Category</th>
                    <th scope="col" className="px-4 py-3 text-right font-medium">Price</th>
                    <th scope="col" className="px-4 py-3 text-right font-medium">Sizes in stock</th>
                    <th scope="col" className="px-4 py-3 text-right font-medium">Pairs</th>
                    <th scope="col" className="px-4 py-3 font-medium">Stock</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {rows.map(({ p, s }) => (
                    <tr key={p.id} className="transition-colors duration-(--duration-base) hover:bg-surface-1">
                      <th scope="row" className="px-4 py-2 font-medium">
                        <Link href={`/admin/products/${p.slug}`} className="flex min-h-11 flex-col justify-center text-accent-text underline-offset-4 hover:underline">
                          {p.name}
                          <span className="text-caption font-normal uppercase text-fg-muted no-underline">{p.brand}</span>
                        </Link>
                      </th>
                      <td className="px-4 py-3 text-fg-muted">{p.categoryName}</td>
                      <td className="tabular px-4 py-3 text-right text-fg">{formatPrice(p.minPriceMinor)}</td>
                      <td className="tabular px-4 py-3 text-right text-fg-muted">{s.sizes} of {s.total}</td>
                      <td className="tabular px-4 py-3 text-right text-fg">{s.units}</td>
                      <td className="px-4 py-3"><Badge tone={s.level === "sold" ? "danger" : s.level === "low" ? "warning" : "success"}>{s.level === "sold" ? "Sold out" : s.level === "low" ? "Low" : "In stock"}</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <ul className="flex flex-col gap-3 md:hidden">
              {rows.map(({ p, s }) => (
                <li key={p.id}>
                  <Link href={`/admin/products/${p.slug}`} className="flex items-center gap-3 rounded-lg border border-line bg-surface-1 p-4">
                    <div className="min-w-0 flex-1">
                      <p className="text-caption font-medium uppercase text-fg-muted">{p.brand}</p>
                      <p className="truncate text-body font-medium text-fg-strong">{p.name}</p>
                      <p className="tabular text-small text-fg-muted">{formatPrice(p.minPriceMinor)} · {s.units} pairs</p>
                    </div>
                    <Badge tone={s.level === "sold" ? "danger" : s.level === "low" ? "warning" : "success"}>{s.level === "sold" ? "Sold out" : s.level === "low" ? "Low" : "In stock"}</Badge>
                    <ChevronRight aria-hidden="true" className="size-4 text-fg-subtle" />
                  </Link>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </div>
  );
}
