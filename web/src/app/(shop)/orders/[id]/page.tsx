import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { HoldTimer } from "@/components/HoldTimer";
import { OrderDetail } from "@/components/OrderDetail";
import { Alert } from "@/components/ui/Alert";
import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { getOrder, orderRef } from "@/lib/orders";

export async function generateMetadata({ params }: { params: Promise<{ id: string }> }): Promise<Metadata> {
  const o = getOrder((await params).id);
  return { title: o ? `Order ${orderRef(o.id)}` : "Not found" };
}

export default async function OrderPage({ params }: { params: Promise<{ id: string }> }) {
  const order = getOrder((await params).id);
  if (!order) notFound();
  return (
    <Container className="py-8">
      <nav aria-label="Breadcrumb" className="text-small text-fg-subtle">
        <Link href="/orders" className="hover:text-fg">Your orders</Link> <span aria-hidden="true">/</span>{" "}
        <span className="text-fg-muted">{orderRef(order.id)}</span>
      </nav>
      <h1 className="mb-6 mt-3 text-h1 font-semibold text-fg-strong">Order {orderRef(order.id)}</h1>
      <OrderDetail order={order}>
        {order.status === "pending" ? (
          <Alert tone="warning" title="This order is waiting for payment" action={<ButtonLink href={`/orders/${order.id}/pay`}>Pay now</ButtonLink>}>
            <HoldTimer expiresAt={order.expiresAt} now="2026-11-15T16:13:00Z" />
          </Alert>
        ) : null}
      </OrderDetail>
    </Container>
  );
}
