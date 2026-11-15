import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronDown } from "lucide-react";
import { Gallery } from "@/components/Gallery";
import { ProductCard } from "@/components/ProductCard";
import { Purchase } from "@/components/Purchase";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";
import { Badge } from "@/components/ui/Badge";
import { Price } from "@/components/ui/Price";
import { fetchProduct, searchProducts } from "@/lib/api";
import { SHIPPING_FLAT_MINOR, formatPrice, occasionOf } from "@/lib/catalog";

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const p = await fetchProduct((await params).slug).catch(() => null);
  return { title: p ? `${p.name} by ${p.brand}` : "Not found" };
}

export default async function ProductPage({ params }: { params: Promise<{ slug: string }> }) {
  const product = await fetchProduct((await params).slug);
  if (!product) notFound();
  const occasion = occasionOf(product);
  const related = (await searchProducts({ category: product.categorySlug, sort: "newest", pageSize: 5 }).catch(() => null))?.items.filter((p) => p.id !== product.id).slice(0, 4) ?? [];
  const minPriceMinor = Math.min(...product.variants.map((v) => v.priceMinor));
  const inStock = product.variants.some((v) => v.available);
  const colours = [...new Set(product.variants.map((v) => v.colour))];

  return (
    <Container className="py-8">
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

        <div className="lg:sticky lg:top-6 lg:self-start">
          <p className="text-caption font-medium uppercase text-fg-subtle">{product.brand}</p>
          <h1 className="mt-1 text-h1 font-semibold text-fg-strong">{product.name}</h1>
          <div className="mt-3 flex items-center gap-3">
            <Price minor={minPriceMinor} className="text-h2" />
            {!inStock ? <Badge>Sold out</Badge> : null}
          </div>
          <p className="mt-4 text-body text-fg-muted">{product.description}</p>
          {colours.length === 1 ? (
            <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-6 gap-y-1.5 text-small">
              <dt className="text-fg-subtle">{colours.length === 1 ? "Colour" : "Colours"}</dt>
              <dd className="text-fg capitalize">{colours.join(", ")}</dd>
            </dl>
          ) : null}

          <div className="mt-6">
            <Purchase product={product} />
          </div>

          <div className="mt-8 divide-y divide-line border-y border-line">
            <details className="group py-1" open>
              <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between text-small font-medium text-fg">
                Shipping and payment
                <ChevronDown aria-hidden="true" className="size-4 text-fg-subtle group-open:rotate-180" />
              </summary>
              <p className="pb-4 text-small text-fg-muted">
                Flat-rate shipping of {formatPrice(SHIPPING_FLAT_MINOR)} per order. You pay on Stripe&rsquo;s hosted page in test mode, so no card is charged.
              </p>
            </details>
          </div>
        </div>
      </div>

      {related.length ? (
        <Section id="related" title={`More in ${occasion?.label}`} href={`/products?occasion=${occasion?.slug}`}>
          <ul className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-4">
            {related.map((p) => (
              <li key={p.id}>
                <ProductCard product={p} />
              </li>
            ))}
          </ul>
        </Section>
      ) : null}
    </Container>
  );
}
