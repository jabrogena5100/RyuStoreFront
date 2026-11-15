import { Ruler, ShieldCheck, Truck } from "lucide-react";
import { SHIPPING_FLAT_MINOR, formatPrice } from "@/lib/catalog";

/** Three statements the product can back today (flat rate in the API config, Stripe test mode, size picker with sold-out sizes). */
export function ValueStrip() {
  const items = [
    { icon: Truck, title: `Flat ${formatPrice(SHIPPING_FLAT_MINOR)} shipping`, text: "One rate per order, whatever is in the cart." },
    { icon: ShieldCheck, title: "Stripe test mode", text: "Pay on Stripe's page; no real card is charged." },
    { icon: Ruler, title: "US sizes, live stock", text: "Every pair shows which sizes are still available." },
  ];
  return (
    <ul className="grid divide-y divide-line rounded-lg border border-line bg-surface-1 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
      {items.map(({ icon: Icon, title, text }) => (
        <li key={title} className="flex items-start gap-4 p-5">
          <span className="grid size-11 shrink-0 place-items-center rounded-pill bg-surface-3 text-fg">
            <Icon aria-hidden="true" className="size-5" />
          </span>
          <div>
            <p className="text-body font-medium text-fg-strong">{title}</p>
            <p className="mt-0.5 text-small text-fg-muted">{text}</p>
          </div>
        </li>
      ))}
    </ul>
  );
}
