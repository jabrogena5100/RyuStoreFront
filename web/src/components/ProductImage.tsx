import clsx from "clsx";
import type { ProductSummary } from "@/lib/catalog";

type View = 0 | 1 | 2 | 3;

/** Where the oversized initial sits in each of the four gallery images, so the images differ and the crop feels deliberate. */
const PLACEMENT: Record<View, { pos: string; size: string; light: string }> = {
  0: { pos: "-left-[7%] -bottom-[17%]", size: "text-[92cqw]", light: "26% 10%" },
  1: { pos: "-right-[7%] -top-[11%]", size: "text-[92cqw]", light: "74% 90%" },
  2: { pos: "left-[6%] -bottom-[44%]", size: "text-[140cqw]", light: "50% 0%" },
  3: { pos: "-right-[11%] -bottom-[17%]", size: "text-[92cqw]", light: "80% 8%" },
};

/**
 * Placeholder for a product photo: a 4:5 tone-on-tone panel with a soft light and an oversized initial of the model name.
 * It does not try to draw a shoe. Real photos replace it at the same ratio. (design/BRIEF.md, section 4)
 */
export function ProductImage({
  product,
  view = 0,
  className,
  label = true,
  decorative = false,
}: {
  product: Pick<ProductSummary, "brand" | "name" | "categorySlug">;
  view?: View;
  className?: string;
  label?: boolean;
  /** True when the surrounding link or button already names the product. */
  decorative?: boolean;
}) {
  const initial = (product.name.trim()[0] ?? "?").toUpperCase();
  const p = PLACEMENT[view];
  return (
    <div
      {...(decorative ? { "aria-hidden": true } : { role: "img", "aria-label": `${product.brand} ${product.name} (placeholder image)` })}
      className={clsx("@container relative aspect-[4/5] w-full overflow-hidden", className)}
      style={{ background: `radial-gradient(120% 90% at ${p.light}, var(--color-surface-3), var(--color-surface-1) 72%)` }}
    >
      {/* Generated content, not text: pure decoration that no assistive tech or contrast checker should read. */}
      <span
        aria-hidden="true"
        data-glyph={initial}
        className={clsx("pointer-events-none absolute select-none font-semibold leading-none tracking-tighter text-veil after:content-[attr(data-glyph)]", p.size, p.pos)}
      />
      {label ? <p className="absolute left-4 top-4 text-caption font-medium uppercase text-fg-subtle">{product.brand}</p> : null}
    </div>
  );
}
