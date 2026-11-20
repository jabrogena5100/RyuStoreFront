import Link from "next/link";
import { Container } from "@/components/ui/Container";
import { CATEGORY_LINKS, QUICK_LINKS } from "@/components/Header";
import { SHIPPING_FLAT_MINOR, formatPrice } from "@/lib/catalog";

function Column({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h2 className="font-mono text-caption uppercase text-fg-muted">{title}</h2>
      <ul className="mt-3 flex flex-col">{children}</ul>
    </div>
  );
}

const link = "inline-flex min-h-11 items-center text-small text-fg-muted transition-colors duration-(--duration-base) hover:text-fg";

/* Layout pattern (link columns under a brand block, a rule, a bottom line) follows a 21st.dev footer; see docs/decisions.md. */
export function Footer() {
  return (
    <footer className="mt-16 border-t border-line bg-canvas sm:mt-24">
      <Container className="grid gap-10 py-12 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1fr]">
        <div className="max-w-sm sm:col-span-2 lg:col-span-1">
          <p className="flex items-baseline gap-2 text-fg-strong">
            <span className="text-h2 font-bold tracking-tighter">RYU</span>{" "}
            <span className="font-mono text-caption uppercase text-fg-muted">Storefront</span>
          </p>
          <p className="mt-3 text-small text-fg-muted">
            Sneakers, runners, boots and evening shoes, by occasion. A portfolio project: nothing here ships.
          </p>
        </div>
        <Column title="Shop">
          {CATEGORY_LINKS.map((l) => (
            <li key={l.href}>
              <Link href={l.href} className={link}>{l.label}</Link>
            </li>
          ))}
        </Column>
        <Column title="Browse">
          {QUICK_LINKS.map((l) => (
            <li key={l.href}>
              <Link href={l.href} className={link}>{l.label}</Link>
            </li>
          ))}
          <li>
            <Link href="/cart" className={link}>Your cart</Link>
          </li>
          <li>
            <Link href="/orders" className={link}>Your orders</Link>
          </li>
          <li>
            <Link href="/sign-in" className={link}>Sign in</Link>
          </li>
        </Column>
        <Column title="How it works">
          <li className="py-2 text-small text-fg-muted">Flat {formatPrice(SHIPPING_FLAT_MINOR)} shipping per order</li>
          <li className="py-2 text-small text-fg-muted">Pay with Stripe in test mode: no card is charged</li>
          <li className="py-2 text-small text-fg-muted">US sizes, in-stock sizes shown on every pair</li>
        </Column>
      </Container>
      <div className="border-t border-line">
        <Container className="flex flex-wrap items-center justify-between gap-x-6 py-3 text-caption text-fg-muted">
          <span className="py-2 font-mono uppercase">Demo store · Stripe test mode, no card is charged · Brands and products are invented</span>
          <span className="flex gap-6">
            <Link href="/credits" className="inline-flex min-h-11 items-center hover:text-fg">Image credits</Link>
            <Link href="/admin" className="inline-flex min-h-11 items-center hover:text-fg">Admin (demo)</Link>
          </span>
        </Container>
      </div>
    </footer>
  );
}
