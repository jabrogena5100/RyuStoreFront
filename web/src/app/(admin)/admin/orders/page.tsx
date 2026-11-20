import type { Metadata } from "next";
import clsx from "clsx";
import Link from "next/link";
import { ChevronRight, Search } from "lucide-react";
import { Forbidden } from "@/components/admin/Forbidden";
import { StatusBadge } from "@/components/OrderParts";
import { ButtonLink } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/StatePanel";
import { ORDERS, STATUS_LABEL, fmtDate, itemCount, money, orderRef, type OrderStatus } from "@/lib/orders";

export const metadata: Metadata = { title: "Orders · Admin" };

type Raw = { state?: string; status?: string; q?: string };
const STATUSES = Object.keys(STATUS_LABEL) as OrderStatus[];
const href = (p: Raw) => {
  const s = new URLSearchParams();
  if (p.status) s.set("status", p.status);
  if (p.q) s.set("q", p.q);
  return s.toString() ? `/admin/orders?${s}` : "/admin/orders";
};

export default async function AdminOrders({ searchParams }: { searchParams: Promise<Raw> }) {
  const { state, status, q } = await searchParams;
  const needle = q?.trim().toLowerCase();
  const matchQ = (o: (typeof ORDERS)[number]) => !needle || `${orderRef(o.id)} ${o.contactEmail} ${o.customerName}`.toLowerCase().includes(needle);
  const base = ORDERS.filter(matchQ);
  const rows = base.filter((o) => !status || o.status === status).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const chip = "inline-flex min-h-11 items-center gap-2 rounded-pill border px-4 text-small font-medium whitespace-nowrap transition-colors duration-(--duration-base)";

  return (
    <div>
      <h1 className="text-h1 font-semibold text-fg-strong">Orders</h1>
      {state === "forbidden" ? (
        <div className="mt-6"><Forbidden /></div>
      ) : (
        <>
          <form action="/admin/orders" role="search" className="mt-5 flex max-w-xl">
            {status ? <input type="hidden" name="status" value={status} /> : null}
            <label htmlFor="q" className="sr-only">Search orders by reference, name or e-mail</label>
            <input id="q" name="q" type="search" defaultValue={q} placeholder="Search by reference, name or e-mail" className="min-h-11 min-w-0 flex-1 rounded-l-md border border-r-0 border-line-control bg-surface-1 px-4 text-small text-fg placeholder:text-fg-muted" />
            <button type="submit" aria-label="Search" className="grid min-h-11 w-12 place-items-center rounded-r-md bg-accent text-on-accent transition-colors duration-(--duration-base) hover:bg-accent-hover">
              <Search aria-hidden="true" className="size-4" />
            </button>
          </form>
          <ul aria-label="Filter by status" className="no-scrollbar -mx-4 mt-4 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:flex-wrap sm:px-0">
            <li>
              <Link href={href({ q })} aria-current={!status ? "true" : undefined} className={clsx(chip, !status ? "border-accent-line bg-accent-soft text-accent-text" : "border-line bg-surface-1 text-fg-muted hover:bg-surface-2 hover:text-fg")}>
                All <span className="tabular text-fg-muted">{base.length}</span>
              </Link>
            </li>
            {STATUSES.map((s) => {
              const n = base.filter((o) => o.status === s).length;
              return (
                <li key={s}>
                  <Link href={href({ status: status === s ? undefined : s, q })} aria-current={status === s ? "true" : undefined} className={clsx(chip, status === s ? "border-accent-line bg-accent-soft text-accent-text" : "border-line bg-surface-1 text-fg-muted hover:bg-surface-2 hover:text-fg")}>
                    {STATUS_LABEL[s]} <span className="tabular text-fg-muted">{n}</span>
                  </Link>
                </li>
              );
            })}
          </ul>

          <div className="mt-6">
            {state === "loading" ? (
              <div role="status" aria-busy="true" aria-label="Loading orders" className="flex flex-col gap-3">
                {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-14 w-full rounded-lg" />)}
              </div>
            ) : state === "error" ? (
              <ErrorState title="We could not load the orders" action={<ButtonLink href="/admin/orders">Try again</ButtonLink>}>
                Nothing was changed. Try again in a moment.
              </ErrorState>
            ) : state === "empty" || rows.length === 0 ? (
              <EmptyState title="No orders match" action={<ButtonLink href="/admin/orders" variant="secondary">Clear filters</ButtonLink>}>
                Try another status or search.
              </EmptyState>
            ) : (
              <>
                <div className="hidden overflow-hidden rounded-lg border border-line md:block">
                  <table className="w-full text-left text-small">
                    <caption className="sr-only">Orders, newest first</caption>
                    <thead className="bg-surface-1 text-caption uppercase text-fg-muted">
                      <tr>
                        <th scope="col" className="px-4 py-3 font-medium">Order</th>
                        <th scope="col" className="px-4 py-3 font-medium">Placed</th>
                        <th scope="col" className="px-4 py-3 font-medium">Customer</th>
                        <th scope="col" className="px-4 py-3 text-right font-medium">Items</th>
                        <th scope="col" className="px-4 py-3 text-right font-medium">Total</th>
                        <th scope="col" className="px-4 py-3 font-medium">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-line">
                      {rows.map((o) => (
                        <tr key={o.id} className="transition-colors duration-(--duration-base) hover:bg-surface-1">
                          <th scope="row" className="px-4 py-3 font-medium">
                            <Link href={`/admin/orders/${o.id}`} className="inline-flex min-h-11 items-center text-accent-text underline-offset-4 hover:underline">{orderRef(o.id)}</Link>
                          </th>
                          <td className="px-4 py-3 text-fg-muted">{fmtDate(o.createdAt)}</td>
                          <td className="px-4 py-3"><span className="text-fg">{o.customerName}</span><br /><span className="text-fg-subtle">{o.contactEmail}</span></td>
                          <td className="tabular px-4 py-3 text-right text-fg-muted">{itemCount(o)}</td>
                          <td className="tabular px-4 py-3 text-right font-medium text-fg-strong">{money(o.totalMinor)}</td>
                          <td className="px-4 py-3"><StatusBadge status={o.status} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <ul className="flex flex-col gap-3 md:hidden">
                  {rows.map((o) => (
                    <li key={o.id}>
                      <Link href={`/admin/orders/${o.id}`} className="flex items-center gap-3 rounded-lg border border-line bg-surface-1 p-4">
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="text-body font-medium text-fg-strong">{orderRef(o.id)}</span>
                            <StatusBadge status={o.status} />
                          </div>
                          <p className="mt-1 truncate text-small text-fg-muted">{o.customerName} · {fmtDate(o.createdAt)}</p>
                        </div>
                        <span className="tabular text-body font-semibold text-fg-strong">{money(o.totalMinor)}</span>
                        <ChevronRight aria-hidden="true" className="size-4 text-fg-subtle" />
                      </Link>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}
