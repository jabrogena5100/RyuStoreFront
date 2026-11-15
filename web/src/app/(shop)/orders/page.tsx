import type { Metadata } from "next";
import Link from "next/link";
import { PackageOpen } from "lucide-react";
import { ProductImage } from "@/components/ProductImage";
import { StatusBadge } from "@/components/OrderParts";
import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/StatePanel";
import { ORDERS, fmtDate, itemCount, money, orderRef } from "@/lib/orders";

export const metadata: Metadata = { title: "Your orders" };

/** ?state=loading|empty|error|signedout shows each state for design review; the list is Ada Buyer's fixture orders. */
export default async function Orders({ searchParams }: { searchParams: Promise<{ state?: string }> }) {
  const { state } = await searchParams;
  const mine = ORDERS.filter((o) => o.customerName === "Ada Buyer").sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return (
    <Container className="py-8">
      <h1 className="mb-6 text-h1 font-semibold text-fg-strong">Your orders</h1>
      {state === "loading" ? (
        <ul aria-busy="true" aria-label="Loading your orders" className="flex flex-col gap-4">
          {[0, 1, 2].map((i) => (
            <li key={i} className="flex gap-4 rounded-lg border border-line p-5">
              <Skeleton className="h-20 w-16" />
              <div className="flex flex-1 flex-col gap-3">
                <Skeleton className="h-4 w-40" />
                <Skeleton className="h-5 w-64" />
                <Skeleton className="h-4 w-24" />
              </div>
            </li>
          ))}
        </ul>
      ) : state === "error" ? (
        <ErrorState title="We could not load your orders" action={<ButtonLink href="/orders">Try again</ButtonLink>}>
          Your orders are safe. Check your connection and try again.
        </ErrorState>
      ) : state === "signedout" ? (
        <EmptyState title="Sign in to see your orders" action={<ButtonLink href="/sign-in">Sign in</ButtonLink>}>
          Your orders are saved to your account.
        </EmptyState>
      ) : state === "empty" || mine.length === 0 ? (
        <EmptyState title="No orders yet" action={<ButtonLink href="/products">Browse all shoes</ButtonLink>}>
          Your first order will show up here, with its status.
        </EmptyState>
      ) : (
        <ul className="flex flex-col gap-4">
          {mine.map((o) => (
            <li key={o.id}>
              <Link href={`/orders/${o.id}`} className="group flex flex-col gap-4 rounded-lg border border-line bg-surface-1 p-4 transition-colors duration-(--duration-base) hover:border-line-strong sm:flex-row sm:items-center sm:p-5">
                <div className="relative w-14 shrink-0 overflow-hidden rounded-md border border-line sm:w-16">
                  <ProductImage product={{ brand: o.items[0].brand, name: o.items[0].productName, categorySlug: o.items[0].categorySlug }} label={false} decorative />
                  {o.items.length > 1 ? (
                    <span aria-hidden="true" className="tabular absolute bottom-1 right-1 rounded-pill bg-surface-3 px-1.5 text-caption font-semibold text-fg">+{o.items.length - 1}</span>
                  ) : null}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <p className="text-body font-medium text-fg-strong">Order {orderRef(o.id)}</p>
                    <StatusBadge status={o.status} />
                  </div>
                  <p className="mt-1 truncate text-small text-fg-muted">{o.items.map((i) => i.productName).join(", ")}</p>
                  <p className="text-small text-fg-subtle">
                    {fmtDate(o.createdAt)} · {itemCount(o)} {itemCount(o) === 1 ? "item" : "items"}
                  </p>
                </div>
                <div className="flex items-center justify-between gap-4 sm:flex-col sm:items-end sm:justify-center">
                  <span className="tabular text-body font-semibold text-fg-strong">{money(o.totalMinor)}</span>
                  <span className="inline-flex items-center gap-1 text-small font-medium text-accent-text group-hover:underline">
                    <PackageOpen aria-hidden="true" className="size-4" />
                    View order
                  </span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Container>
  );
}
