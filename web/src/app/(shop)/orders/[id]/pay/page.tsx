import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { LivePay } from "@/components/LiveOrders";
import { PayBody, type PayState } from "@/components/OrderViews";
import { getOrder } from "@/lib/orders";

export const metadata: Metadata = { title: "Pay" };

const REVIEW: Record<string, PayState> = { idle: "idle", starting: "starting", provider: "provider", unavailable: "unavailable", expired: "expired" };

/** ?state=idle|starting|provider|unavailable|expired shows each state with a fixture order (design review); without it, the real pay step. */
export default async function PayPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ state?: string }> }) {
  const { id } = await params;
  const { state } = await searchParams;
  if (!state) return <LivePay id={id} />;
  const order = getOrder(id);
  if (!order) notFound();
  return <PayBody order={order} state={REVIEW[state] ?? "idle"} now="2026-11-15T16:13:00Z" />;
}
