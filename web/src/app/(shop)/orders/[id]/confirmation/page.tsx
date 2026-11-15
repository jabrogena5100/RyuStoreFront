import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { LiveConfirmation } from "@/components/LiveOrders";
import { ConfirmationBody } from "@/components/OrderViews";
import { getOrder, withStatus, type OrderStatus } from "@/lib/orders";

export const metadata: Metadata = { title: "Order confirmation" };

const SHOW: Record<string, OrderStatus> = { confirming: "pending", paid: "paid", cancelled: "cancelled", refunded: "refunded" };

/**
 * Where Stripe sends the shopper back to. Arriving proves nothing: the live page polls the order until the webhook has marked it
 * paid. ?state=confirming|paid|cancelled|refunded shows each case with a fixture order (design review).
 */
export default async function Confirmation({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ state?: string }> }) {
  const { id } = await params;
  const { state } = await searchParams;
  if (!state) return <LiveConfirmation id={id} />;
  const base = getOrder(id);
  if (!base) notFound();
  return <ConfirmationBody order={withStatus(base, SHOW[state] ?? "paid")} />;
}
