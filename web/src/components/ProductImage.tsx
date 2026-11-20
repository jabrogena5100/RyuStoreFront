import clsx from "clsx";
import { IMAGE_WIDTHS, type ImageView } from "@/lib/catalog";

type View = 0 | 1 | 2 | 3;

/** Where the oversized initial sits in each of the four gallery images, so the images differ and the crop feels deliberate. */
const PLACEMENT: Record<View, { pos: string; size: string; light: string }> = {
  0: { pos: "-left-[7%] -bottom-[17%]", size: "text-[92cqw]", light: "26% 10%" },
  1: { pos: "-right-[7%] -top-[11%]", size: "text-[92cqw]", light: "74% 90%" },
  2: { pos: "left-[6%] -bottom-[44%]", size: "text-[140cqw]", light: "50% 0%" },
  3: { pos: "-right-[11%] -bottom-[17%]", size: "text-[92cqw]", light: "80% 8%" },
};

/** `${base}-480.avif 480w, ...`: the variants scripts/images/variants.mjs writes for every photo. */
// A variant is never wider than its original (sharp does not enlarge), so the descriptor is the real width: a 1,280 px original
// serves its "1440" file as 1280w.
const srcset = (img: ImageView, fmt: "avif" | "webp") =>
  [...new Set(IMAGE_WIDTHS.map((w) => Math.min(w, img.width)))]
    .map((real) => `${img.base}-${IMAGE_WIDTHS.find((w) => Math.min(w, img.width) === real)}.${fmt} ${real}w`)
    .join(", ");

/**
 * A product photo when there is one: AVIF with a WebP fallback at three widths, the original's width and height so the
 * browser reserves the space (no layout shift), lazy unless `priority`. Cropped to the frame with object-fit: cover.
 * Without a photo, the typographic placeholder below.
 *
 * Placeholder for a product photo: a 4:3 tone-on-tone panel with a soft light and an oversized initial of the model name.
 * It does not try to draw a shoe. Real photos replace it at the same ratio. (design/BRIEF.md, section 4)
 */
export function ProductImage({
  product,
  view = 0,
  className,
  label = true,
  decorative = false,
  image,
  sizes = "(min-width: 1024px) 25vw, 50vw",
  priority = false,
}: {
  product: { brand: string; name: string; categorySlug?: string };
  view?: View;
  className?: string;
  label?: boolean;
  /** True when the surrounding link or button already names the product. */
  decorative?: boolean;
  /** The photo to show; the placeholder is drawn when it is missing. */
  image?: ImageView | null;
  /** How wide the frame is at each breakpoint, so the browser picks the smallest variant that fills it. */
  sizes?: string;
  /** Above the fold: load eagerly with high priority (the page's LCP image). */
  priority?: boolean;
}) {
  if (image) {
    return (
      <picture className={clsx("relative block aspect-[4/3] w-full overflow-hidden bg-surface-1", className)}>
        <source type="image/avif" srcSet={srcset(image, "avif")} sizes={sizes} />
        <source type="image/webp" srcSet={srcset(image, "webp")} sizes={sizes} />
        {/* eslint-disable-next-line @next/next/no-img-element -- pre-built variants from object storage, no optimiser needed */}
        <img
          src={`${image.base}-960.webp`}
          width={image.width}
          height={image.height}
          alt={decorative ? "" : image.alt}
          loading={priority ? "eager" : "lazy"}
          fetchPriority={priority ? "high" : "auto"}
          decoding="async"
          className="absolute inset-0 h-full w-full object-cover"
        />
      </picture>
    );
  }
  const initial = (product.name.trim()[0] ?? "?").toUpperCase();
  const p = PLACEMENT[view];
  return (
    <div
      {...(decorative ? { "aria-hidden": true } : { role: "img", "aria-label": `${product.brand} ${product.name} (placeholder image)` })}
      className={clsx("@container relative aspect-[4/3] w-full overflow-hidden", className)}
      style={{ background: `radial-gradient(120% 90% at ${p.light}, var(--color-surface-3), var(--color-surface-1) 72%)` }}
    >
      {/* Generated content, not text: pure decoration that no assistive tech or contrast checker should read. */}
      <span
        aria-hidden="true"
        data-glyph={initial}
        className={clsx("pointer-events-none absolute select-none font-semibold leading-none tracking-tighter text-veil after:content-[attr(data-glyph)]", p.size, p.pos)}
      />
      {label ? <p className="absolute left-4 top-4 text-caption font-medium uppercase text-fg-muted">{product.brand}</p> : null}
    </div>
  );
}
