import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { LiveOrderPage } from "@/components/LiveOrders";
import { OrderPageBody } from "@/components/OrderViews";
import { getOrder } from "@/lib/orders";

export const metadata: Metadata = { title: "Your order" };

/** With ?state=review the page shows a fixture order (design review); without it, the signed-in customer's real order. */
export default async function OrderPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ state?: string }> }) {
  const { id } = await params;
  const { state } = await searchParams;
  if (!state) return <LiveOrderPage id={id} />;
  const order = getOrder(id);
  if (!order) notFound();
  return <OrderPageBody order={order} now="2026-11-15T16:13:00Z" />;
}
