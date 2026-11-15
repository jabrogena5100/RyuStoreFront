import Link from "next/link";
import { ProductImage } from "@/components/ProductImage";
import { Badge } from "@/components/ui/Badge";
import { STATUS_LABEL, STATUS_TONE, fmtDateTime, money, type Address, type OrderItem, type OrderStatus, type StatusEvent } from "@/lib/orders";

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <Badge tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</Badge>;
}

/** One order or cart line: picture, names, size and colour, quantity and price. */
export function ItemRow({ item, linkTo = true }: { item: Pick<OrderItem, "productSlug" | "brand" | "productName" | "colour" | "sizeLabel" | "quantity" | "lineTotalMinor">; linkTo?: boolean }) {
  const pic = (
    <div className="w-16 shrink-0 overflow-hidden rounded-md border border-line sm:w-20">
      <ProductImage product={{ brand: item.brand, name: item.productName }} label={false} decorative />
    </div>
  );
  return (
    <div className="flex gap-4">
      {linkTo ? (
        <Link href={`/products/${item.productSlug}`} aria-label={`${item.productName} product page`}>
          {pic}
        </Link>
      ) : (
        pic
      )}
      <div className="flex min-w-0 flex-1 items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-caption font-medium uppercase text-fg-subtle">{item.brand}</p>
          <p className="text-body font-medium text-fg-strong">{item.productName}</p>
          <p className="text-small text-fg-muted">
            <span className="capitalize">{item.colour}</span> · Size {item.sizeLabel} · Qty {item.quantity}
          </p>
        </div>
        <span className="tabular shrink-0 text-body font-medium text-fg-strong">{money(item.lineTotalMinor)}</span>
      </div>
    </div>
  );
}

export function Totals({ subtotalMinor, shippingMinor, totalMinor, count }: { subtotalMinor: number; shippingMinor: number; totalMinor: number; count?: number }) {
  return (
    <dl className="flex flex-col gap-2 text-small">
      <div className="flex justify-between">
        <dt className="text-fg-muted">Subtotal{count ? ` (${count} ${count === 1 ? "item" : "items"})` : ""}</dt>
        <dd className="tabular text-fg">{money(subtotalMinor)}</dd>
      </div>
      <div className="flex justify-between">
        <dt className="text-fg-muted">Shipping (flat rate)</dt>
        <dd className="tabular text-fg">{money(shippingMinor)}</dd>
      </div>
      <div className="mt-2 flex justify-between border-t border-line pt-3 text-body">
        <dt className="font-medium text-fg-strong">Total</dt>
        <dd className="tabular font-semibold text-fg-strong">{money(totalMinor)}</dd>
      </div>
    </dl>
  );
}

export function AddressBlock({ address, email }: { address: Address; email?: string }) {
  return (
    <address className="text-small not-italic leading-relaxed text-fg">
      {address.name}
      <br />
      {address.line1}
      {address.line2 ? (
        <>
          <br />
          {address.line2}
        </>
      ) : null}
      <br />
      {address.city}, {address.region} {address.postalCode}
      <br />
      {address.country}
      {email ? (
        <>
          <br />
          <span className="text-fg-muted">{email}</span>
        </>
      ) : null}
    </address>
  );
}

/** The order's status changes, oldest first. The last one is the current state. */
export function StatusTimeline({ history }: { history: StatusEvent[] }) {
  return (
    <ol aria-label="Order history" className="flex flex-col">
      {history.map((e, i) => {
        const last = i === history.length - 1;
        return (
          <li key={e.status + e.at} className="flex gap-3">
            <div className="flex flex-col items-center">
              <span aria-hidden="true" className={last ? "mt-1.5 size-3 rounded-pill bg-accent" : "mt-1.5 size-3 rounded-pill border border-line-strong"} />
              {last ? null : <span aria-hidden="true" className="w-px flex-1 bg-line" />}
            </div>
            <div className="pb-5">
              <p className="text-small font-medium text-fg-strong">{STATUS_LABEL[e.status]}</p>
              <p className="text-small text-fg-subtle">{fmtDateTime(e.at)}</p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
