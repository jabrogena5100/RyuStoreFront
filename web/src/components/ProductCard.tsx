import Link from "next/link";
import { Badge } from "@/components/ui/Badge";
import { Price } from "@/components/ui/Price";
import { ProductImage } from "@/components/ProductImage";
import type { ProductSummary } from "@/lib/catalog";

/*
 * Product card. Adapted from "ProductCard" by youcefbnm on 21st.dev (https://21st.dev/@youcefbnm/components/product-card),
 * library "Systaliko UI", licence shown there: MIT. Kept from the original: the second image fading in over the first on
 * hover. Changed: no motion library (a 180 ms CSS opacity fade, removed under reduced motion), also on keyboard focus, no colour
 * dots (the API has no colours per product yet), and the card carries brand, name, category and price in the design tokens.
 * See design/COMPONENT-NOTES.md.
 */
export function ProductCard({ product }: { product: ProductSummary }) {
  return (
    <Link href={`/products/${product.slug}`} className="group block rounded-md focus-visible:outline-offset-4">
      <div className="relative overflow-hidden rounded-md bg-surface-1">
        <ProductImage product={product} label={false} decorative />
        <div className="absolute inset-0 opacity-0 transition-opacity duration-(--duration-base) ease-standard group-hover:opacity-100 group-focus-visible:opacity-100">
          <ProductImage product={product} view={1} label={false} decorative className="h-full" />
        </div>
        {!product.inStock ? (
          <div className="absolute left-2.5 top-2.5">
            <Badge>Sold out</Badge>
          </div>
        ) : null}
      </div>
      <div className="mt-3 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-mono text-caption uppercase text-fg-muted">{product.brand}</p>
          <h3 className="mt-0.5 line-clamp-2 text-small font-medium text-fg-strong sm:text-body">{product.name}</h3>
          <p className="mt-0.5 text-small text-fg-muted">{product.categoryName}</p>
        </div>
        <Price minor={product.minPriceMinor} currency={product.currency} className="shrink-0 pt-4 text-small sm:text-body" />
      </div>
    </Link>
  );
}
