"use client";

import { useRef } from "react";
import { Button } from "@/components/ui/Button";
import { NEXT_STATUS, STATUS_LABEL, money, type OrderStatus } from "@/lib/orders";

const VERB: Partial<Record<OrderStatus, string>> = { fulfilled: "Mark as packed", shipped: "Mark as shipped" };

/** Only the moves the lifecycle allows are offered (docs/order-lifecycle.md); everything else is simply absent. Refund asks first. */
export function OrderActions({ status, totalMinor }: { status: OrderStatus; totalMinor: number }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const next = NEXT_STATUS[status];
  const forward = next.filter((n) => n !== "refunded");
  const canRefund = next.includes("refunded");

  return (
    <section aria-label="Actions" className="rounded-lg border border-line bg-surface-1 p-5">
      <h2 className="mb-3 text-small font-semibold text-fg-strong">Next steps</h2>
      {next.length === 0 ? (
        <p className="text-small text-fg-muted">
          {status === "pending" ? "Waiting for the customer to pay. Nothing to do until Stripe confirms the payment." : `${STATUS_LABEL[status]} is final. No further changes are possible.`}
        </p>
      ) : (
        <div className="flex flex-wrap gap-3">
          {forward.map((n) => (
            <Button key={n}>{VERB[n]}</Button>
          ))}
          {canRefund ? (
            <Button variant="secondary" onClick={() => dialog.current?.showModal()}>
              Refund order
            </Button>
          ) : null}
        </div>
      )}
      <dialog
        ref={dialog}
        aria-labelledby="refund-title"
        onClick={(e) => {
          if (e.target === dialog.current) dialog.current?.close();
        }}
        className="m-auto w-[min(28rem,calc(100vw-2rem))] rounded-lg border border-line bg-surface-1 p-0 text-fg shadow-overlay"
      >
        <div className="p-6">
          <h2 id="refund-title" className="text-h2 font-semibold text-fg-strong">Refund {money(totalMinor)}?</h2>
          <p className="mt-3 text-small text-fg-muted">
            This returns the full payment to the customer through Stripe (test mode here) and moves the order to Refunded. It cannot be undone, and a refunded order cannot be changed again.
          </p>
          <div className="mt-6 flex flex-wrap justify-end gap-3">
            <Button variant="secondary" onClick={() => dialog.current?.close()}>Keep order</Button>
            <Button variant="danger" onClick={() => dialog.current?.close()}>Refund {money(totalMinor)}</Button>
          </div>
        </div>
      </dialog>
    </section>
  );
}
