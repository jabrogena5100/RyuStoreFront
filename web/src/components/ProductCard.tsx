import Link from "next/link";
import { Badge } from "@/components/ui/Badge";
import { Price } from "@/components/ui/Price";
import { ProductImage } from "@/components/ProductImage";
import type { ProductSummary } from "@/lib/catalog";

/**
 * Product card. Pattern from the 21st.dev shortlist ("ProductCard" by youcefbnm: a second image on hover), adapted:
 * the second view cross-fades with opacity only (180 ms, removed under reduced motion), no scale, no shadow, no glow.
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
