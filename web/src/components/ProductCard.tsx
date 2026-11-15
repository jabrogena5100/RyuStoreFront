import Link from "next/link";
import { Badge } from "@/components/ui/Badge";
import { Price } from "@/components/ui/Price";
import { ProductImage } from "@/components/ProductImage";
import type { ProductSummary } from "@/lib/catalog";

export function ProductCard({ product, priority = false }: { product: ProductSummary; priority?: boolean }) {
  return (
    <Link
      href={`/products/${product.slug}`}
      className="group block rounded-lg focus-visible:outline-offset-4"
    >
      <div className="relative overflow-hidden rounded-lg border border-line">
        <div className="transition-transform duration-(--duration-slower) ease-emphasized [@media(hover:hover)]:group-hover:scale-[1.03]">
          <ProductImage product={product} label={false} decorative />
        </div>
        {!product.inStock ? (
          <div className="absolute left-3 top-3">
            <Badge>Sold out</Badge>
          </div>
        ) : null}
      </div>
      <div className="mt-3 flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
        <div className="min-w-0">
          <p className="text-caption font-medium uppercase text-fg-subtle">{product.brand}</p>
          <h3 className="line-clamp-2 text-body font-medium text-fg-strong">{product.name}</h3>
          <p className="text-small text-fg-muted capitalize">{product.colour}</p>
        </div>
        <Price minor={product.minPriceMinor} currency={product.currency} className="text-body sm:pt-4" />
      </div>
    </Link>
  );
}
