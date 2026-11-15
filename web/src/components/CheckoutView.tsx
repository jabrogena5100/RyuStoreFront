"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Lock } from "lucide-react";
import { useCart } from "@/components/CartProvider";
import { ItemRow, Totals } from "@/components/OrderParts";
import { Alert } from "@/components/ui/Alert";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Field, SelectField } from "@/components/ui/Field";
import { EmptyState } from "@/components/ui/StatePanel";
import { ORDERS } from "@/lib/orders";

/** Messages for the field codes in docs/api/checkout.md (required, length, invalid_country, invalid_email). */
const ERRORS = {
  name: "Enter the name for the delivery.",
  line1: "Enter the street address.",
  city: "Enter the city.",
  postalCode: "Enter the postal code.",
} as const;

/** Per-line problems for a `cart_changed` response, keyed by position in the cart for review: `unavailable` and `stock`. */
const CHANGED = [{ code: "unavailable" as const }, { code: "stock" as const, left: 1 }];

const FILLED = { name: "Ada Buyer", line1: "1 Main St", line2: "Apt 4", city: "Portland", region: "OR", postalCode: "97201", country: "US" };

/**
 * Checkout, all states. `state` is for design review: (none) idle, errors, submitting, failed, changed, signedout, empty.
 * "Place order" goes to the pay step with a fixture order; the real call is POST /api/checkout with an Idempotency-Key.
 */
export function CheckoutView({ state }: { state?: string }) {
  const router = useRouter();
  const { lines, subtotalMinor, shippingMinor, totalMinor, count, remove, setQty } = useCart();
  const busy = state === "submitting";
  const showErrors = state === "errors";
  const filled = busy || state === "failed" || state === "changed";
  const v = showErrors ? { ...FILLED, name: "", city: "", postalCode: "" } : filled ? FILLED : { name: "", line1: "", line2: "", city: "", region: "", postalCode: "", country: "US" };
  const err = (k: keyof typeof ERRORS) => (showErrors && !v[k] ? ERRORS[k] : undefined);
  const changed = state === "changed";

  if (state === "empty" || lines.length === 0) {
    return (
      <EmptyState title="Your cart is empty" action={<ButtonLink href="/products">Browse all shoes</ButtonLink>}>
        There is nothing to check out. Add a pair and come back.
      </EmptyState>
    );
  }

  const summary = (
    <aside aria-label="Order summary" className="rounded-lg border border-line bg-surface-1 p-5 lg:sticky lg:top-6">
      <h2 className="text-h2 font-semibold text-fg-strong">Your order</h2>
      <ul className="mt-4 flex flex-col gap-4">
        {lines.map((l, i) => {
          const problem = changed ? CHANGED[i] : undefined;
          return (
            <li key={l.sku} className="flex flex-col gap-2">
              <ItemRow item={{ slug: l.slug, brand: l.brand, productName: l.name, categorySlug: l.categorySlug, colour: l.colour, sizeLabel: l.sizeLabel, quantity: l.qty, unitPriceMinor: l.priceMinor, lineTotalMinor: l.priceMinor * l.qty }} linkTo={false} />
              {problem ? (
                <div className="ml-20 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md bg-surface-2 px-3 py-2 text-small">
                  <span className="font-medium text-warning">{problem.code === "unavailable" ? "No longer available" : `Only ${problem.left} left`}</span>
                  {problem.code === "unavailable" ? (
                    <button type="button" onClick={() => remove(l.sku)} className="min-h-11 text-accent-text underline-offset-4 hover:underline">
                      Remove
                    </button>
                  ) : (
                    <button type="button" onClick={() => setQty(l.sku, problem.left)} className="min-h-11 text-accent-text underline-offset-4 hover:underline">
                      Reduce to {problem.left}
                    </button>
                  )}
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>
      <div className="mt-5 border-t border-line pt-5">
        <Totals subtotalMinor={subtotalMinor} shippingMinor={shippingMinor} totalMinor={totalMinor} count={count} />
      </div>
      <Button type="submit" form="checkout-form" size="lg" disabled={busy || changed} aria-disabled={busy || changed || undefined} className="mt-5 w-full">
        <Lock aria-hidden="true" className="size-5" />
        {busy ? "Placing order" : "Place order"}
      </Button>
      <p className="mt-3 text-caption text-fg-subtle">
        {changed ? "Fix the items above to continue. " : ""}You pay on Stripe&rsquo;s page in test mode, so no real card is charged.
      </p>
    </aside>
  );

  return (
    <div className="grid items-start gap-8 lg:grid-cols-[1fr_24rem] lg:gap-12">
      <div className="flex flex-col gap-6">
        {state === "signedout" ? (
          <Alert
            tone="info"
            title="Sign in to place your order"
            action={
              <div className="flex flex-wrap gap-3">
                <ButtonLink href="/sign-in?next=checkout">Sign in</ButtonLink>
                <ButtonLink href="/register?next=checkout" variant="secondary">Create account</ButtonLink>
              </div>
            }
          >
            You can fill in your details first. Your bag comes with you when you sign in.
          </Alert>
        ) : null}
        {showErrors ? <Alert tone="danger" title="Check the highlighted fields">Some details are missing. Nothing has been ordered.</Alert> : null}
        {state === "failed" ? (
          <Alert tone="danger" title="We could not place your order" action={<Button type="submit" form="checkout-form" variant="secondary">Try again</Button>}>
            Nothing was charged and your bag is unchanged. Trying again will not create a second order.
          </Alert>
        ) : null}
        {changed ? <Alert tone="warning" title="Some items in your bag changed">Another shopper took a pair. Fix the marked items, then place your order.</Alert> : null}

        <form
          id="checkout-form"
          noValidate
          aria-busy={busy || undefined}
          onSubmit={(e) => {
            e.preventDefault();
            router.push(`/orders/${ORDERS[0].id}/pay`);
          }}
          className="flex flex-col gap-8"
        >
          <fieldset disabled={busy} className="flex flex-col gap-4">
            <legend className="mb-4 text-h2 font-semibold text-fg-strong">Contact</legend>
            <Field id="email" name="contactEmail" type="email" label="E-mail" autoComplete="email" defaultValue="ada@example.com" hint="Your order confirmation goes here." />
          </fieldset>
          <fieldset disabled={busy} className="flex flex-col gap-4">
            <legend className="mb-4 text-h2 font-semibold text-fg-strong">Shipping address</legend>
            <Field id="name" name="name" label="Full name" autoComplete="name" defaultValue={v.name} error={err("name")} />
            <Field id="line1" name="line1" label="Address" autoComplete="address-line1" defaultValue={v.line1} error={err("line1")} />
            <Field id="line2" name="line2" label="Apartment, suite (optional)" autoComplete="address-line2" defaultValue={v.line2} />
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="city" name="city" label="City" autoComplete="address-level2" defaultValue={v.city} error={err("city")} />
              <Field id="region" name="region" label="State or region" autoComplete="address-level1" defaultValue={v.region} />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="postalCode" name="postalCode" label="Postal code" autoComplete="postal-code" defaultValue={v.postalCode} error={err("postalCode")} />
              <SelectField id="country" name="country" label="Country" autoComplete="country" defaultValue={v.country}>
                <option value="US">United States</option>
                <option value="CA">Canada</option>
                <option value="GB">United Kingdom</option>
              </SelectField>
            </div>
          </fieldset>
          <p className="text-small text-fg-muted">
            Shipping is a flat rate for every order. <Link href="/cart" className="text-accent-text underline underline-offset-4">Edit your bag</Link>
          </p>
        </form>
      </div>
      {summary}
    </div>
  );
}
