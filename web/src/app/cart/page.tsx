import type { Metadata } from "next";
import { CartView } from "@/components/CartView";
import { SeedCart } from "@/components/CartProvider";
import { demoCartLines } from "@/lib/catalog";

export const metadata: Metadata = { title: "Cart" };

/** ?state=filled|loading|error shows each state for design review; with no state it is the live cart. */
export default async function CartPage({ searchParams }: { searchParams: Promise<{ state?: string }> }) {
  const { state } = await searchParams;
  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <h1 className="mb-6 text-h1 font-semibold text-fg-strong">Your cart</h1>
      {state === "filled" ? <SeedCart lines={demoCartLines()} /> : null}
      <CartView state={state} />
    </div>
  );
}
