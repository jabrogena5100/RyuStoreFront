import type { Metadata } from "next";
import { CheckoutView } from "@/components/CheckoutView";
import { SeedCart } from "@/components/CartProvider";
import { Container } from "@/components/ui/Container";
import { Steps } from "@/components/ui/Steps";
import { demoCartLines } from "@/lib/catalog";

export const metadata: Metadata = { title: "Checkout" };

/** ?state=errors|submitting|failed|changed|signedout|empty shows each state for design review; with no state it is the live checkout. */
export default async function CheckoutPage({ searchParams }: { searchParams: Promise<{ state?: string }> }) {
  const { state } = await searchParams;
  return (
    <Container className="py-8">
      <Steps current={1} />
      <h1 className="mb-8 mt-4 text-h1 font-semibold text-fg-strong">Checkout</h1>
      {state && state !== "empty" ? <SeedCart lines={demoCartLines()} /> : null}
      <CheckoutView state={state} />
    </Container>
  );
}
