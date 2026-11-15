"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Lock } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useAuth } from "@/components/AuthProvider";
import { useCart } from "@/components/CartProvider";
import { ItemRow, Totals } from "@/components/OrderParts";
import { Alert } from "@/components/ui/Alert";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Field, SelectField } from "@/components/ui/Field";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/StatePanel";
import { ApiClientError, api } from "@/lib/api-client";
import { ORDERS } from "@/lib/orders";

type Phase = "idle" | "submitting" | "errors" | "failed" | "changed" | "signedout";
type Problem = "unavailable" | "stock";

/** Messages for the field codes in docs/api/checkout.md (required, length, invalid_country, invalid_email). */
const TEXT: Record<string, Record<string, string>> = {
  name: { required: "Enter the name for the delivery.", length: "Use 100 characters or fewer." },
  line1: { required: "Enter the street address.", length: "Use 100 characters or fewer." },
  line2: { length: "Use 100 characters or fewer." },
  city: { required: "Enter the city.", length: "Use 60 characters or fewer." },
  region: { required: "Enter the state or region.", length: "Use 60 characters or fewer." },
  postalCode: { required: "Enter the postal code.", length: "Use 20 characters or fewer." },
  country: { required: "Choose a country.", invalid_country: "Choose a country." },
  contactEmail: { required: "Enter your e-mail.", length: "E-mails can be up to 100 characters.", invalid_email: "Enter a valid e-mail address." },
};

const FILLED = { name: "Ada Buyer", line1: "1 Main St", line2: "Apt 4", city: "Portland", region: "OR", postalCode: "97201", country: "US" };
const BLANK = { name: "", line1: "", line2: "", city: "", region: "", postalCode: "", country: "US" };

/**
 * Checkout, all states. Live behaviour: signed-in customers POST /api/checkout (with an Idempotency-Key kept for as long as the
 * form content is unchanged, so a retry returns the same order) and go to the pay step; guests are asked to sign in first.
 * `state` is for design review only: errors, submitting, failed, changed, signedout, empty (fixture cart, no API calls).
 */
export function CheckoutView({ state }: { state?: string }) {
  const router = useRouter();
  const { customer } = useAuth();
  const { lines, subtotalMinor, shippingMinor, totalMinor, count, remove, setQty, status, refresh } = useCart();
  const review = state !== undefined;

  const [phase, setPhase] = useState<Phase>(
    state === "errors" ? "errors" : state === "submitting" ? "submitting" : state === "failed" ? "failed" : state === "changed" ? "changed" : state === "signedout" ? "signedout" : "idle",
  );
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>(state === "errors" ? { name: "required", city: "required", postalCode: "required" } : {});
  const [problems, setProblems] = useState<Record<string, Problem>>({});
  const [email, setEmail] = useState(review ? "ada@example.com" : "");
  const touched = useRef(false);
  const attempt = useRef<{ body: string; key: string } | null>(null);

  // Prefill the e-mail with the account's once the session is known, unless the shopper already typed one.
  useEffect(() => {
    if (customer && !touched.current && !review) setEmail(customer.email);
  }, [customer, review]);

  const busy = phase === "submitting";
  const values = state === "errors" ? { ...FILLED, name: "", city: "", postalCode: "" } : phase === "submitting" || state === "failed" || state === "changed" ? FILLED : BLANK;
  const err = (name: string) => (fieldErrors[name] ? (TEXT[name]?.[fieldErrors[name]] ?? "Check this field.") : undefined);
  // Review mode shows two fixed line problems; live mode shows what the API said, by SKU.
  const problemFor = (sku: string, index: number): Problem | undefined => (state === "changed" ? (["unavailable", "stock"] as const)[index] : problems[sku]);
  const open = lines.some((l, i) => problemFor(l.sku, i));

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (review) {
      router.push(`/orders/${ORDERS[0].id}/pay?state=idle`); // review mode: no API calls
      return;
    }
    if (customer === null) {
      setPhase("signedout");
      return;
    }
    if (customer === undefined) return;
    const f = new FormData(e.currentTarget);
    const text = (k: string) => String(f.get(k) ?? "").trim();
    const body = {
      contactEmail: email.trim() || undefined,
      shippingAddress: { name: text("name"), line1: text("line1"), line2: text("line2"), city: text("city"), region: text("region"), postalCode: text("postalCode"), country: text("country") },
    };
    const json = JSON.stringify(body);
    // One key per attempt: the same content reuses it (a retry returns the same order); changed content gets a new one.
    if (attempt.current?.body !== json) attempt.current = { body: json, key: crypto.randomUUID() };
    setPhase("submitting");
    setFieldErrors({});
    try {
      const order = await api<{ id: string }>("/checkout", { method: "POST", body, headers: { "Idempotency-Key": attempt.current.key } });
      router.push(`/orders/${order.id}/pay`); // stays "submitting" while the page changes
    } catch (e2) {
      if (e2 instanceof ApiClientError && e2.status === 401) setPhase("signedout");
      else if (e2 instanceof ApiClientError && e2.code === "validation_failed") {
        setFieldErrors(Object.fromEntries(Object.entries(e2.fields).map(([k, v]) => [k.replace(/^shippingAddress\./, ""), v])));
        setPhase("errors");
      } else if (e2 instanceof ApiClientError && e2.code === "empty_cart") {
        await refresh();
        setPhase("idle");
      } else if (e2 instanceof ApiClientError && e2.code === "cart_changed") {
        setProblems(Object.fromEntries(Object.entries(e2.fields).map(([sku, code]) => [sku, code === "stock" ? "stock" : "unavailable"])) as Record<string, Problem>);
        await refresh();
        setPhase("changed");
      } else setPhase("failed");
    }
  }

  async function fix(sku: string, action: () => Promise<unknown>) {
    await action();
    setProblems((p) => {
      const { [sku]: _gone, ...rest } = p;
      if (Object.keys(rest).length === 0) setPhase("idle");
      return rest;
    });
  }

  if (!review && status === "loading") {
    return (
      <div role="status" aria-busy="true" aria-label="Loading checkout" className="grid gap-8 lg:grid-cols-[1fr_24rem]">
        <Skeleton className="h-96 w-full rounded-lg" />
        <Skeleton className="h-96 w-full rounded-lg" />
      </div>
    );
  }
  if (!review && status === "error") {
    return (
      <ErrorState title="We could not load your bag" action={<Button onClick={() => void refresh()}>Try again</Button>}>
        Your items are safe. Check your connection and try again.
      </ErrorState>
    );
  }
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
          const problem = problemFor(l.sku, i);
          const left = state === "changed" ? 1 : Math.max(l.maxQuantity, 0); // review mode shows one pair left
          return (
            <li key={l.sku} className="flex flex-col gap-2">
              <ItemRow item={{ productSlug: l.slug, brand: l.brand, productName: l.name, colour: l.colour, sizeLabel: l.sizeLabel, quantity: l.qty, lineTotalMinor: l.priceMinor * l.qty }} linkTo={false} />
              {problem ? (
                <div className="ml-20 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md bg-surface-2 px-3 py-2 text-small">
                  <span className="font-medium text-warning">{problem === "unavailable" ? "No longer available" : `Only ${left} left`}</span>
                  {problem === "unavailable" || left < 1 ? (
                    <button type="button" onClick={() => void fix(l.sku, () => remove(l.sku))} className="min-h-11 text-accent-text underline underline-offset-4">
                      Remove
                    </button>
                  ) : (
                    <button type="button" onClick={() => void fix(l.sku, () => setQty(l.sku, left))} className="min-h-11 text-accent-text underline underline-offset-4">
                      Reduce to {left}
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
      <Button type="submit" form="checkout-form" size="lg" disabled={busy || open} aria-disabled={busy || open || undefined} className="mt-5 w-full">
        <Lock aria-hidden="true" className="size-5" />
        {busy ? "Placing order" : "Place order"}
      </Button>
      <p className="mt-3 text-caption text-fg-subtle">
        {open ? "Fix the items above to continue. " : ""}You pay on Stripe&rsquo;s page in test mode, so no real card is charged.
      </p>
    </aside>
  );

  return (
    <div className="grid items-start gap-8 lg:grid-cols-[1fr_24rem] lg:gap-12">
      <div className="flex flex-col gap-6">
        {phase === "signedout" ? (
          <Alert
            tone="info"
            title="Sign in to place your order"
            action={
              <div className="flex flex-wrap gap-3">
                <ButtonLink href="/sign-in?next=%2Fcheckout">Sign in</ButtonLink>
                <ButtonLink href="/register?next=%2Fcheckout" variant="secondary">Create account</ButtonLink>
              </div>
            }
          >
            Your details stay in the form and your bag comes with you when you sign in.
          </Alert>
        ) : null}
        {phase === "errors" ? <Alert tone="danger" title="Check the highlighted fields">Some details are missing or not valid. Nothing has been ordered.</Alert> : null}
        {phase === "failed" ? (
          <Alert tone="danger" title="We could not place your order" action={<Button type="submit" form="checkout-form" variant="secondary">Try again</Button>}>
            Nothing was charged and your bag is unchanged. Trying again will not create a second order.
          </Alert>
        ) : null}
        {phase === "changed" ? <Alert tone="warning" title="Some items in your bag changed">Another shopper took a pair. Fix the marked items, then place your order.</Alert> : null}

        <form id="checkout-form" noValidate aria-busy={busy || undefined} onSubmit={submit} className="flex flex-col gap-8">
          <fieldset disabled={busy} className="flex flex-col gap-4">
            <legend className="mb-4 text-h2 font-semibold text-fg-strong">Contact</legend>
            <Field
              id="email"
              name="contactEmail"
              type="email"
              label="E-mail"
              autoComplete="email"
              value={email}
              onChange={(e) => {
                touched.current = true;
                setEmail(e.target.value);
              }}
              error={err("contactEmail")}
              hint="Your order confirmation goes here."
            />
          </fieldset>
          <fieldset disabled={busy} className="flex flex-col gap-4">
            <legend className="mb-4 text-h2 font-semibold text-fg-strong">Shipping address</legend>
            <Field id="name" name="name" label="Full name" autoComplete="name" defaultValue={values.name} error={err("name")} />
            <Field id="line1" name="line1" label="Address" autoComplete="address-line1" defaultValue={values.line1} error={err("line1")} />
            <Field id="line2" name="line2" label="Apartment, suite (optional)" autoComplete="address-line2" defaultValue={values.line2} error={err("line2")} />
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="city" name="city" label="City" autoComplete="address-level2" defaultValue={values.city} error={err("city")} />
              <Field id="region" name="region" label="State or region" autoComplete="address-level1" defaultValue={values.region} error={err("region")} />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="postalCode" name="postalCode" label="Postal code" autoComplete="postal-code" defaultValue={values.postalCode} error={err("postalCode")} />
              <SelectField id="country" name="country" label="Country" autoComplete="country" defaultValue={values.country} error={err("country")}>
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
