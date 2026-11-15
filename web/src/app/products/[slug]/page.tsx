import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronDown } from "lucide-react";
import { Gallery } from "@/components/Gallery";
import { ProductCard } from "@/components/ProductCard";
import { Purchase } from "@/components/Purchase";
import { Badge } from "@/components/ui/Badge";
import { Price } from "@/components/ui/Price";
import { PRODUCTS, SHIPPING_FLAT_MINOR, formatPrice, getProduct, occasionOf } from "@/lib/catalog";

export function generateStaticParams() {
  return PRODUCTS.map((p) => ({ slug: p.slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const p = getProduct((await params).slug);
  return { title: p ? `${p.name} by ${p.brand}` : "Not found" };
}

export default async function ProductPage({ params }: { params: Promise<{ slug: string }> }) {
  const product = getProduct((await params).slug);
  if (!product) notFound();
  const occasion = occasionOf(product);
  const related = PRODUCTS.filter((p) => p.id !== product.id && occasionOf(p)?.slug === occasion?.slug).slice(0, 4);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <nav aria-label="Breadcrumb" className="text-small text-fg-subtle">
        <Link href="/" className="hover:text-fg">Home</Link> <span aria-hidden="true">/</span>{" "}
        {occasion ? (
          <>
            <Link href={`/products?occasion=${occasion.slug}`} className="hover:text-fg">{occasion.label}</Link> <span aria-hidden="true">/</span>{" "}
          </>
        ) : null}
        <span className="text-fg-muted">{product.name}</span>
      </nav>

      <div className="mt-6 grid gap-8 lg:grid-cols-[1.15fr_1fr] lg:gap-14">
        <Gallery product={product} />

        <div className="lg:sticky lg:top-24 lg:self-start">
          <p className="text-caption font-medium uppercase text-fg-subtle">{product.brand}</p>
          <h1 className="mt-1 text-h1 font-semibold text-fg-strong">{product.name}</h1>
          <div className="mt-3 flex items-center gap-3">
            <Price minor={product.minPriceMinor} className="text-h2" />
            {!product.inStock ? <Badge>Sold out</Badge> : null}
          </div>
          <p className="mt-4 text-body text-fg-muted">{product.description}</p>
          <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-6 gap-y-1.5 text-small">
            <dt className="text-fg-subtle">Colour</dt>
            <dd className="text-fg capitalize">{product.colour}</dd>
            <dt className="text-fg-subtle">Upper</dt>
            <dd className="text-fg capitalize">{product.material}</dd>
          </dl>

          <div className="mt-6">
            <Purchase product={product} />
          </div>

          <div className="mt-8 divide-y divide-line border-y border-line">
            <details className="group py-1" open>
              <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between text-small font-medium text-fg">
                Details
                <ChevronDown aria-hidden="true" className="size-4 text-fg-subtle transition-transform duration-(--duration-base) group-open:rotate-180" />
              </summary>
              <ul className="list-disc space-y-1.5 pb-4 pl-5 text-small text-fg-muted">
                {product.details.map((d) => (
                  <li key={d}>{d}</li>
                ))}
              </ul>
            </details>
            <details className="group py-1">
              <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between text-small font-medium text-fg">
                Materials
                <ChevronDown aria-hidden="true" className="size-4 text-fg-subtle transition-transform duration-(--duration-base) group-open:rotate-180" />
              </summary>
              <p className="pb-4 text-small text-fg-muted capitalize">{product.material}</p>
            </details>
            <details className="group py-1">
              <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between text-small font-medium text-fg">
                Shipping and payment
                <ChevronDown aria-hidden="true" className="size-4 text-fg-subtle transition-transform duration-(--duration-base) group-open:rotate-180" />
              </summary>
              <p className="pb-4 text-small text-fg-muted">
                Flat-rate shipping of {formatPrice(SHIPPING_FLAT_MINOR)} per order. You pay on Stripe&rsquo;s hosted page in test mode, so no card is charged.
              </p>
            </details>
          </div>
        </div>
      </div>

      {related.length ? (
        <section aria-labelledby="related" className="mt-20">
          <h2 id="related" className="mb-5 text-h2 font-semibold text-fg-strong">
            More in {occasion?.label}
          </h2>
          <ul className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-4">
            {related.map((p) => (
              <li key={p.id}>
                <ProductCard product={p} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
