import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { OrderActions } from "@/components/admin/OrderActions";
import { OrderDetail } from "@/components/OrderDetail";
import { Alert } from "@/components/ui/Alert";
import { getOrder, orderRef } from "@/lib/orders";

export const metadata: Metadata = { title: "Order · Admin" };

/** ?state=conflict shows the 409 "changed while you were looking" case. */
export default async function AdminOrder({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ state?: string }> }) {
  const order = getOrder((await params).id);
  const { state } = await searchParams;
  if (!order) notFound();
  return (
    <div>
      <nav aria-label="Breadcrumb" className="text-small text-fg-subtle">
        <Link href="/admin/orders" className="hover:text-fg">Orders</Link> <span aria-hidden="true">/</span> <span className="text-fg-muted">{orderRef(order.id)}</span>
      </nav>
      <h1 className="mb-6 mt-3 text-h1 font-semibold text-fg-strong">Order {orderRef(order.id)}</h1>
      {state === "conflict" ? (
        <Alert tone="danger" title="This order changed while you were looking" className="mb-6" action={<Link href={`/admin/orders/${order.id}`} className="text-small font-medium text-accent-text underline-offset-4 hover:underline">Reload the order</Link>}>
          Another action got there first, so yours was not applied. Check the status below and try again.
        </Alert>
      ) : null}
      <OrderDetail order={order} admin>
        <OrderActions status={order.status} totalMinor={order.totalMinor} />
      </OrderDetail>
    </div>
  );
}
