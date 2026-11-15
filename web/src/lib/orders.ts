/*
 * Design-phase orders. The Order shape is the checkout response in docs/api/checkout.md. Everything marked PROPOSED
 * does not exist in the backend yet: order history and admin endpoints, status history, per-item product link and brand
 * (the snapshot has only productName), and variant stock counts. They are listed in docs/api/proposed-ui-needs.md.
 */
import { PRODUCTS, formatPrice, type Product } from "@/lib/catalog";

export type OrderStatus = "pending" | "paid" | "fulfilled" | "shipped" | "cancelled" | "refunded";

export const STATUS_LABEL: Record<OrderStatus, string> = {
  pending: "Awaiting payment",
  paid: "Paid",
  fulfilled: "Packed",
  shipped: "Shipped",
  cancelled: "Cancelled",
  refunded: "Refunded",
};

export const STATUS_TONE: Record<OrderStatus, "neutral" | "accent" | "success" | "warning" | "danger"> = {
  pending: "warning",
  paid: "success",
  fulfilled: "accent",
  shipped: "accent",
  cancelled: "neutral",
  refunded: "warning",
};

/** The rules in docs/order-lifecycle.md: what an admin may do next. Anything else is not offered. */
export const NEXT_STATUS: Record<OrderStatus, OrderStatus[]> = {
  pending: [],
  paid: ["fulfilled", "refunded"],
  fulfilled: ["shipped", "refunded"],
  shipped: ["refunded"],
  cancelled: [],
  refunded: [],
};

export interface Address {
  name: string;
  line1: string;
  line2: string | null;
  city: string;
  region: string;
  postalCode: string;
  country: string;
}

export interface OrderItem {
  sku: string;
  productName: string;
  sizeLabel: string;
  colour: string;
  unitPriceMinor: number;
  quantity: number;
  lineTotalMinor: number;
  /** PROPOSED: the snapshot needs these two to link and picture the item. */
  slug: string;
  brand: string;
  categorySlug: string;
}

export interface StatusEvent {
  status: OrderStatus;
  at: string;
}

export interface Order {
  id: string;
  status: OrderStatus;
  contactEmail: string;
  currency: "USD";
  subtotalMinor: number;
  shippingMinor: number;
  totalMinor: number;
  shippingAddress: Address;
  expiresAt: string;
  createdAt: string;
  items: OrderItem[];
  /** PROPOSED: from order_status_history. */
  history: StatusEvent[];
  /** PROPOSED: admin list only. */
  customerName: string;
}

const SHIPPING = 800;

function line(p: Product, qty: number, size?: string): OrderItem {
  const v = (size ? p.variants.find((x) => x.sizeLabel === size) : undefined) ?? p.variants.find((x) => x.available) ?? p.variants[0];
  return {
    sku: v.sku,
    productName: p.name,
    sizeLabel: v.sizeLabel,
    colour: v.colour,
    unitPriceMinor: v.priceMinor,
    quantity: qty,
    lineTotalMinor: v.priceMinor * qty,
    slug: p.slug,
    brand: p.brand,
    categorySlug: p.categorySlug,
  };
}

const bySlug = (slug: string) => PRODUCTS.find((p) => p.slug === slug)!;

function make(
  id: string,
  customerName: string,
  email: string,
  address: Address,
  createdAt: string,
  items: OrderItem[],
  history: [OrderStatus, string][],
): Order {
  const subtotal = items.reduce((n, i) => n + i.lineTotalMinor, 0);
  const created = new Date(createdAt);
  return {
    id,
    status: history[history.length - 1][0],
    contactEmail: email,
    currency: "USD",
    subtotalMinor: subtotal,
    shippingMinor: SHIPPING,
    totalMinor: subtotal + SHIPPING,
    shippingAddress: address,
    expiresAt: new Date(created.getTime() + 36 * 60_000).toISOString(),
    createdAt,
    items,
    history: history.map(([status, at]) => ({ status, at })),
    customerName,
  };
}

const ADA: Address = { name: "Ada Buyer", line1: "1 Main St", line2: "Apt 4", city: "Portland", region: "OR", postalCode: "97201", country: "US" };
const NOOR: Address = { name: "Noor Haddad", line1: "88 Harbor Rd", line2: null, city: "Seattle", region: "WA", postalCode: "98101", country: "US" };
const LEO: Address = { name: "Leo Marsh", line1: "12 Elm Ct", line2: null, city: "Austin", region: "TX", postalCode: "78701", country: "US" };

export const ORDERS: Order[] = [
  make("a2715f79-927b-4833-a8bc-73815e0ea58e", "Ada Buyer", "ada@example.com", ADA, "2026-11-15T16:05:00Z",
    [line(bySlug("vale-carbon-racer"), 1, "9")], [["pending", "2026-11-15T16:05:00Z"]]),
  make("5c0d7a14-3e21-4b6f-9a58-1f2c8e4d7b90", "Ada Buyer", "ada@example.com", ADA, "2026-11-12T18:20:00Z",
    [line(bySlug("aurel-court-sneaker"), 2, "6"), line(bySlug("marais-chelsea-boot"), 1, "5")],
    [["pending", "2026-11-12T18:20:00Z"], ["paid", "2026-11-12T18:23:00Z"], ["fulfilled", "2026-11-13T09:10:00Z"], ["shipped", "2026-11-13T15:42:00Z"]]),
  make("e91b3c68-0d47-4f2a-8c15-6a7d2b9e0f34", "Noor Haddad", "noor@example.com", NOOR, "2026-11-14T10:02:00Z",
    [line(bySlug("harlan-combat-boot"), 1, "8")], [["pending", "2026-11-14T10:02:00Z"], ["paid", "2026-11-14T10:04:00Z"]]),
  make("0b4f6e2d-71a9-4c83-b5d0-9e3a1c7f8a62", "Leo Marsh", "leo@example.com", LEO, "2026-11-13T21:33:00Z",
    [line(bySlug("soren-penny-loafer"), 1, "10"), line(bySlug("kai-slide"), 1)],
    [["pending", "2026-11-13T21:33:00Z"], ["paid", "2026-11-13T21:36:00Z"], ["fulfilled", "2026-11-14T08:15:00Z"]]),
  make("7d2e9a05-c6b1-4e38-a417-3f8b0d5c2e19", "Ada Buyer", "ada@example.com", ADA, "2026-11-09T13:48:00Z",
    [line(bySlug("sela-tempo-shoe"), 1, "7")], [["pending", "2026-11-09T13:48:00Z"], ["cancelled", "2026-11-09T14:24:00Z"]]),
  make("3a8c1f70-5e94-4d2b-b6a3-8c0e7d1f9b45", "Noor Haddad", "noor@example.com", NOOR, "2026-11-07T11:15:00Z",
    [line(bySlug("ione-ankle-boot"), 1, "7.5")],
    [["pending", "2026-11-07T11:15:00Z"], ["paid", "2026-11-07T11:18:00Z"], ["refunded", "2026-11-08T09:30:00Z"]]),
];

export function getOrder(id: string): Order | undefined {
  return ORDERS.find((o) => o.id === id);
}

/** The short reference shown to people: the first eight characters of the id, upper case. */
export const orderRef = (id: string) => id.slice(0, 8).toUpperCase();

const date = new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeZone: "UTC" });
const time = new Intl.DateTimeFormat("en-US", { timeStyle: "short", timeZone: "UTC" });
/** Dates are shown in UTC so the server and the browser agree; the label says so where time of day matters. */
export const fmtDate = (iso: string) => date.format(new Date(iso));
export const fmtDateTime = (iso: string) => `${date.format(new Date(iso))}, ${time.format(new Date(iso))} UTC`;
export const fmtTime = (iso: string) => `${time.format(new Date(iso))} UTC`;

export const itemCount = (o: Order) => o.items.reduce((n, i) => n + i.quantity, 0);

/** PROPOSED: stock per variant for the admin product screen (the public API only exposes "available"). */
export function stockOf(p: Product): { sku: string; sizeLabel: string; priceMinor: number; stock: number }[] {
  return p.variants.map((v, i) => ({
    sku: v.sku,
    sizeLabel: v.sizeLabel,
    priceMinor: v.priceMinor,
    stock: v.available ? ((p.id * 7 + i * 3) % 9) + 1 : 0,
  }));
}

export const money = formatPrice;

/** Design review only: the same order shown in another status, with a matching history. */
export function withStatus(o: Order, status: OrderStatus): Order {
  const path: OrderStatus[] = ["pending", "paid", "fulfilled", "shipped"];
  const t0 = new Date(o.createdAt).getTime();
  const at = (m: number) => new Date(t0 + m * 60_000).toISOString();
  let history: StatusEvent[];
  if (status === "cancelled") history = [{ status: "pending", at: at(0) }, { status: "cancelled", at: at(36) }];
  else if (status === "refunded") history = [{ status: "pending", at: at(0) }, { status: "paid", at: at(3) }, { status: "refunded", at: at(4) }];
  else history = path.slice(0, path.indexOf(status) + 1).map((st, i) => ({ status: st, at: at(i === 0 ? 0 : i * 3) }));
  return { ...o, status, history };
}
