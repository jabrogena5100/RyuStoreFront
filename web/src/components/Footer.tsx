import Link from "next/link";
import { OCCASIONS } from "@/lib/catalog";

export function Footer() {
  return (
    <footer className="mt-24 border-t border-line">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-10 sm:px-6 md:grid-cols-[1fr_auto] lg:px-8">
        <div className="max-w-md">
          <p className="text-body font-semibold tracking-tight text-fg-strong">
            Storefront<span className="text-accent-text">.</span>
          </p>
          <p className="mt-2 text-small text-fg-muted">
            A portfolio project. Payments run through Stripe in test mode, so nothing is charged and nothing ships.
          </p>
        </div>
        <nav aria-label="Footer" className="flex flex-wrap gap-x-6 gap-y-2">
          {OCCASIONS.map((o) => (
            <Link key={o.slug} href={`/products?occasion=${o.slug}`} className="py-2 text-small text-fg-muted hover:text-fg">
              {o.label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  );
}
