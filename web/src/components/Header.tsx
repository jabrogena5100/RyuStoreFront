import Link from "next/link";
import { Search } from "lucide-react";
import { AccountMenu } from "@/components/AccountMenu";
import { CartLink } from "@/components/CartLink";
import { Container } from "@/components/ui/Container";
import { OCCASIONS } from "@/lib/catalog";

/** The category bar: the same destinations as the footer's Shop column. */
export const CATEGORY_LINKS = [
  { href: "/products", label: "All shoes" },
  ...OCCASIONS.map((o) => ({ href: `/products?occasion=${o.slug}`, label: o.label })),
];
export const QUICK_LINKS = [
  { href: "/products?gender=women", label: "Women" },
  { href: "/products?gender=men", label: "Men" },
  { href: "/products?sort=newest", label: "New in" },
  { href: "/products?price=u150", label: "Under $150" },
];

function SearchForm({ className }: { className?: string }) {
  return (
    <form action="/products" role="search" className={className}>
      <label htmlFor="search" className="sr-only">
        Search shoes
      </label>
      <div className="flex">
        <input
          id="search"
          name="q"
          type="search"
          placeholder="Search boots, runners, loafers"
          autoComplete="off"
          className="min-h-11 min-w-0 flex-1 rounded-l-md border border-r-0 border-line-control bg-surface-1 px-4 text-small text-fg placeholder:text-fg-muted"
        />
        <button
          type="submit"
          aria-label="Search"
          className="grid min-h-11 w-12 place-items-center rounded-r-md bg-accent text-on-accent transition-colors duration-(--duration-base) hover:bg-accent-hover"
        >
          <Search aria-hidden="true" className="size-5" />
        </button>
      </div>
    </form>
  );
}

export function Header() {
  return (
    <header className="border-b border-line bg-canvas">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-surface-2 focus:px-4 focus:py-3"
      >
        Skip to content
      </a>
      <Container className="grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-3 py-3 md:grid-cols-[auto_minmax(0,40rem)_auto] md:gap-x-8 md:justify-between">
        <Link href="/" className="flex min-h-11 items-baseline gap-2 text-fg-strong">
          <span className="text-h2 font-bold tracking-tighter">RYU</span>{" "}
          <span className="font-mono text-caption uppercase text-fg-muted">Storefront</span>
        </Link>
        <SearchForm className="order-last col-span-2 md:order-none md:col-span-1" />
        <div className="flex items-center">
          <AccountMenu />
          <CartLink />
        </div>
      </Container>
      <div className="border-t border-line">
        <Container>
          <nav aria-label="Shop" className="no-scrollbar -mx-2 flex items-center overflow-x-auto">
            <ul className="flex items-center">
              {CATEGORY_LINKS.map((l) => (
                <li key={l.href}>
                  <Link href={l.href} className="inline-flex min-h-11 items-center whitespace-nowrap rounded-sm px-3 font-mono text-caption uppercase text-fg-muted transition-colors duration-(--duration-base) hover:text-fg-strong">
                    {l.label}
                  </Link>
                </li>
              ))}
            </ul>
            <span aria-hidden="true" className="mx-2 h-5 w-px shrink-0 bg-line" />
            <ul className="flex items-center">
              {QUICK_LINKS.map((l) => (
                <li key={l.href}>
                  <Link href={l.href} className="inline-flex min-h-11 items-center whitespace-nowrap rounded-sm px-3 font-mono text-caption uppercase text-fg-muted transition-colors duration-(--duration-base) hover:text-fg-strong">
                    {l.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </Container>
      </div>
    </header>
  );
}
