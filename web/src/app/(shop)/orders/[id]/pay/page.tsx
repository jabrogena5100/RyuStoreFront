import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Lock, ShieldCheck } from "lucide-react";
import { HoldTimer } from "@/components/HoldTimer";
import { ItemRow, Totals, AddressBlock } from "@/components/OrderParts";
import { Alert } from "@/components/ui/Alert";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { EmptyState } from "@/components/ui/StatePanel";
import { Steps } from "@/components/ui/Steps";
import { getOrder, itemCount, money } from "@/lib/orders";

export const metadata: Metadata = { title: "Pay" };

/** ?state=starting|unavailable|provider|expired shows each state of the pay step (docs/api/payment.md). */
export default async function PayPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ state?: string }> }) {
  const order = getOrder((await params).id);
  const { state } = await searchParams;
  if (!order) notFound();
  const busy = state === "starting";
  return (
    <Container className="py-8">
      <Steps current={2} />
      <h1 className="mb-8 mt-4 text-h1 font-semibold text-fg-strong">Pay for your order</h1>
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
              <HoldTimer expiresAt={order.expiresAt} now="2026-11-15T16:13:00Z" />
            </div>
            <Button size="lg" disabled={busy} aria-disabled={busy || undefined} className="mt-5 w-full">
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
