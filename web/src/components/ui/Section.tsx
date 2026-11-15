import Link from "next/link";
import clsx from "clsx";
import type { ReactNode } from "react";

/** A titled block of a page: one heading size, one gap above, an optional "see all" link. */
export function Section({
  id,
  title,
  href,
  hrefLabel = "See all",
  className,
  children,
}: {
  id: string;
  title: string;
  href?: string;
  hrefLabel?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section aria-labelledby={id} className={clsx("mt-12 sm:mt-16", className)}>
      <div className="mb-5 flex items-end justify-between gap-4">
        <h2 id={id} className="text-h2 font-semibold text-fg-strong">
          {title}
        </h2>
        {href ? (
          <Link href={href} className="py-2 text-small font-medium text-accent-text hover:underline">
            {hrefLabel}
          </Link>
        ) : null}
      </div>
      {children}
    </section>
  );
}
