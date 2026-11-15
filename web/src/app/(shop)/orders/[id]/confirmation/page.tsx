import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { CircleCheck, Clock, RotateCcw, XCircle } from "lucide-react";
import { OrderDetail } from "@/components/OrderDetail";
import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { Steps } from "@/components/ui/Steps";
import { getOrder, orderRef, withStatus, type OrderStatus } from "@/lib/orders";

export const metadata: Metadata = { title: "Order confirmation" };

const SHOW: Record<string, OrderStatus> = { confirming: "pending", paid: "paid", cancelled: "cancelled", refunded: "refunded" };

/**
 * Landing page after Stripe. Arriving here proves nothing (docs/api/payment.md): the banner follows the order's status.
 * ?state=confirming|paid|cancelled|refunded shows each case for design review by overriding the fixture's status.
 */
export default async function Confirmation({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ state?: string }> }) {
  const base = getOrder((await params).id);
  const { state } = await searchParams;
  if (!base) notFound();
  const order = withStatus(base, SHOW[state ?? "paid"] ?? "paid");
  const s = order.status;

  const banner =
    s === "pending"
      ? { icon: Clock, tone: "text-warning", title: "Confirming your payment", text: "Stripe has told us you are on your way back. We are waiting for the payment to be confirmed; this usually takes a few seconds. You can leave this page: your order is saved." }
      : s === "cancelled"
        ? { icon: XCircle, tone: "text-fg-muted", title: "This order was cancelled", text: "The payment page expired, or the payment arrived after the hold ended. If you were charged, the money is on its way back. Your bag is unchanged." }
        : s === "refunded"
          ? { icon: RotateCcw, tone: "text-warning", title: "Your payment was refunded", text: "We could not keep your pair for this order, so nothing will be shipped. The money is on its way back to your card and should arrive in a few days." }
          : { icon: CircleCheck, tone: "text-success", title: "Thank you, your order is in", text: `We have your payment. A confirmation is on its way to ${order.contactEmail}.` };
  const Icon = banner.icon;

  return (
    <Container className="py-8">
      <Steps current={3} />
      <section aria-labelledby="result" className="mb-8 mt-6 flex gap-4 rounded-lg border border-line bg-surface-1 p-5 sm:p-6">
        <Icon aria-hidden="true" className={`mt-1 size-7 shrink-0 ${banner.tone}`} />
        <div role="status" className="min-w-0">
          <h1 id="result" className="text-h1 font-semibold text-fg-strong">{banner.title}</h1>
          <p className="mt-2 max-w-2xl text-body text-fg-muted">{banner.text}</p>
          <p className="mt-2 text-small text-fg-subtle">Order {orderRef(order.id)}</p>
          <div className="mt-5 flex flex-wrap gap-3">
            {s === "pending" ? <ButtonLink href={`/orders/${order.id}/confirmation`}>Check again</ButtonLink> : <ButtonLink href="/products">Keep shopping</ButtonLink>}
            <ButtonLink href="/orders" variant="secondary">Your orders</ButtonLink>
          </div>
        </div>
      </section>
      <OrderDetail order={order} />
    </Container>
  );
}
