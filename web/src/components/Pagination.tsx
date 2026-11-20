import Link from "next/link";
import clsx from "clsx";
import { ChevronLeft, ChevronRight } from "lucide-react";

/** Numbered pages as links (1-based in the URL). Shows the first, the last and a window around the current page. */
export function Pagination({ page, pages, hrefFor }: { page: number; pages: number; hrefFor: (page: number) => string }) {
  if (pages <= 1) return null;
  const shown = new Set([1, pages, page - 1, page, page + 1]);
  const list = [...shown].filter((n) => n >= 1 && n <= pages).sort((a, b) => a - b);
  const cell = "inline-flex size-11 items-center justify-center rounded-md border text-small font-medium tabular transition-colors duration-(--duration-base)";
  return (
    <nav aria-label="Pages" className="mt-10 flex items-center justify-center gap-2">
      {page > 1 ? (
        <Link href={hrefFor(page - 1)} rel="prev" aria-label="Previous page" className={clsx(cell, "border-line-control text-fg hover:bg-surface-2")}>
          <ChevronLeft aria-hidden="true" className="size-4" />
        </Link>
      ) : null}
      {list.map((n, i) => (
        <span key={n} className="flex items-center gap-2">
          {i > 0 && n - list[i - 1] > 1 ? <span aria-hidden="true" className="text-fg-subtle">…</span> : null}
          <Link
            href={hrefFor(n)}
            aria-label={`Page ${n}`}
            aria-current={n === page ? "page" : undefined}
            className={clsx(cell, n === page ? "border-accent-line bg-accent-soft text-accent-text" : "border-line-control text-fg hover:bg-surface-2")}
          >
            {n}
          </Link>
        </span>
      ))}
      {page < pages ? (
        <Link href={hrefFor(page + 1)} rel="next" aria-label="Next page" className={clsx(cell, "border-line-control text-fg hover:bg-surface-2")}>
          <ChevronRight aria-hidden="true" className="size-4" />
        </Link>
      ) : null}
    </nav>
  );
}
