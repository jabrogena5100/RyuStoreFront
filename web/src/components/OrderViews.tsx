import Link from "next/link";
import { CircleCheck, Clock, Lock, RotateCcw, ShieldCheck, XCircle } from "lucide-react";
import { HoldTimer } from "@/components/HoldTimer";
import { OrderDetail } from "@/components/OrderDetail";
import { AddressBlock, ItemRow, Totals } from "@/components/OrderParts";
import { Alert } from "@/components/ui/Alert";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { EmptyState } from "@/components/ui/StatePanel";
import { Steps } from "@/components/ui/Steps";
import { itemCount, money, orderRef, type ApiOrder } from "@/lib/orders";

/*
 * What the three order screens show, from an order. They take the order as a prop so the same markup serves the review-state
 * fixtures (server-rendered) and live data (the wrappers in LiveOrders.tsx fetch it in the browser). No hooks here.
 */

export function OrderPageBody({ order, now }: { order: ApiOrder; now?: string }) {
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
            <HoldTimer expiresAt={order.expiresAt} now={now} />
          </Alert>
        ) : null}
      </OrderDetail>
    </Container>
  );
}

/** The landing page after Stripe. The banner follows the order's status; arriving here proves nothing (docs/api/payment.md). */
export function ConfirmationBody({ order, onCheckAgain }: { order: ApiOrder; onCheckAgain?: () => void }) {
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
            {s === "pending" ? (
              onCheckAgain ? <Button onClick={onCheckAgain}>Check again</Button> : <ButtonLink href={`/orders/${order.id}/confirmation`}>Check again</ButtonLink>
            ) : (
              <ButtonLink href="/products">Keep shopping</ButtonLink>
            )}
            <ButtonLink href="/orders" variant="secondary">Your orders</ButtonLink>
          </div>
        </div>
      </section>
      <OrderDetail order={order} />
    </Container>
  );
}

export type PayState = "idle" | "starting" | "unavailable" | "provider" | "expired";

/** The pay step: the order, the hold, and the button that sends the shopper to Stripe's page. */
export function PayBody({ order, state, onPay, now }: { order: ApiOrder; state: PayState; onPay?: () => void; now?: string }) {
  const busy = state === "starting";
  return (
    <Container className="py-8">
      <Steps current={2} />
      <h1 className="mb-8 mt-8 text-display font-bold text-fg-strong">Pay for your order</h1>
      {state === "expired" ? (
        <EmptyState title="Your hold ran out" action={<ButtonLink href="/cart">Back to your bag</ButtonLink>}>
          We held your pair for a limited time and it has been released. Your bag is still here; start again to check out.
        </EmptyState>
      ) : (
        <div className="grid items-start gap-8 lg:grid-cols-[1fr_24rem] lg:gap-12">
          <div className="flex flex-col gap-6">
            {state === "unavailable" ? <Alert tone="danger" title="Payments are unavailable right now">Nothing was charged. Your pair stays held; try again in a few minutes.</Alert> : null}
            {state === "provider" ? <Alert tone="danger" title="Stripe did not accept the request">Nothing was charged. It is safe to try again.</Alert> : null}
            <section aria-label="Items" className="rounded-lg border border-line bg-surface-1 p-5">
              <ul className="flex flex-col gap-5">
                {order.items.map((i) => (
                  <li key={i.sku}><ItemRow item={i} linkTo={false} /></li>
                ))}
              </ul>
              <div className="mt-5 border-t border-line pt-5">
                <Totals subtotalMinor={order.subtotalMinor} shippingMinor={order.shippingMinor} totalMinor={order.totalMinor} count={itemCount(order)} />
              </div>
            </section>
            <section className="rounded-lg border border-line bg-surface-1 p-5">
              <h2 className="mb-3 text-small font-semibold text-fg-strong">Shipping to</h2>
              <AddressBlock address={order.shippingAddress} email={order.contactEmail} />
            </section>
          </div>
          <aside aria-label="Payment" className="rounded-lg border border-line bg-surface-1 p-5 lg:sticky lg:top-6">
            <h2 className="text-h2 font-semibold text-fg-strong">Pay with Stripe</h2>
            <p className="tabular mt-3 text-h1 font-semibold text-fg-strong">{money(order.totalMinor)}</p>
            <div className="mt-3">
              <HoldTimer expiresAt={order.expiresAt} now={now} />
            </div>
            <Button size="lg" onClick={onPay} disabled={busy} aria-disabled={busy || undefined} aria-busy={busy || undefined} className="mt-5 w-full">
              <Lock aria-hidden="true" className="size-5" />
              {busy ? "Opening Stripe" : state === "provider" || state === "unavailable" ? "Try again" : "Pay now"}
            </Button>
            <p className="mt-4 flex gap-2 text-small text-fg-muted">
              <ShieldCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              <span>You pay on Stripe&rsquo;s own page. Card details never reach this shop. This is test mode: use card 4242 4242 4242 4242, no real money moves.</span>
            </p>
          </aside>
        </div>
      )}
    </Container>
  );
}
