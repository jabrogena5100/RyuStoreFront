import { AddressBlock, ItemRow, StatusBadge, StatusTimeline, Totals } from "@/components/OrderParts";
import { fmtDate, itemCount, orderRef, type Order } from "@/lib/orders";

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-lg border border-line bg-surface-1 p-5">
      <h2 className="mb-3 text-small font-semibold text-fg-strong">{title}</h2>
      {children}
    </section>
  );
}

/** What was ordered, where it goes and what happened to it. Used by the order page, the confirmation and (with `admin`) the admin order page. */
export function OrderDetail({ order, admin = false, children }: { order: Order; admin?: boolean; children?: React.ReactNode }) {
  return (
    <div className="grid items-start gap-8 lg:grid-cols-[1fr_22rem]">
      <div className="flex flex-col gap-6">
        <section aria-label="Items" className="rounded-lg border border-line bg-surface-1 p-5">
          <ul className="flex flex-col gap-5">
            {order.items.map((i) => (
              <li key={i.sku}>
                <ItemRow item={i} linkTo={!admin} />
              </li>
            ))}
          </ul>
          <div className="mt-5 border-t border-line pt-5">
            <Totals subtotalMinor={order.subtotalMinor} shippingMinor={order.shippingMinor} totalMinor={order.totalMinor} count={itemCount(order)} />
          </div>
        </section>
        {children}
      </div>
      <div className="flex flex-col gap-6">
        <Panel title="Status">
          <div className="mb-4 flex items-center justify-between gap-3">
            <StatusBadge status={order.status} />
            <span className="text-small text-fg-subtle">Placed {fmtDate(order.createdAt)}</span>
          </div>
          <StatusTimeline history={order.history} />
        </Panel>
        <Panel title="Shipping to">
          <AddressBlock address={order.shippingAddress} email={order.contactEmail} />
        </Panel>
        <p className="px-1 text-caption text-fg-subtle">
          Order <span className="text-fg-muted">{orderRef(order.id)}</span>
        </p>
      </div>
    </div>
  );
}
