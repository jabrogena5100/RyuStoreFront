import Link from "next/link";
import clsx from "clsx";
import type { ReactNode } from "react";

/** A titled block of a page: one heading size, one gap above, an optional "see all" link. */
export function Section({
  id,
  title,
  href,
  hrefLabel = "See all",
  index,
  className,
  children,
}: {
  id: string;
  title: string;
  href?: string;
  hrefLabel?: string;
  /** A two-digit mono label above the title ("01"), for the home page's numbered sections. */
  index?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section aria-labelledby={id} className={clsx("mt-12 sm:mt-16", className)}>
      <div className="mb-6 flex items-end justify-between gap-4 border-b border-line pb-4">
        <div>
          {index ? <p className="mb-2 font-mono text-caption uppercase text-fg-muted">{index}</p> : null}
          <h2 id={id} className="text-h2 font-semibold text-fg-strong">
            {title}
          </h2>
        </div>
        {href ? (
          <Link href={href} className="inline-flex min-h-11 items-center gap-1 font-mono text-caption uppercase text-accent-text underline-offset-4 hover:underline">
            {hrefLabel} <span aria-hidden="true">→</span>
          </Link>
        ) : null}
      </div>
      {children}
    </section>
  );
}
