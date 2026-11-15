import clsx from "clsx";
import type { ReactNode } from "react";

/** The one page width and gutter, used by the header, every page and the footer. */
export function Container({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={clsx("mx-auto w-full max-w-7xl px-4 sm:px-6 lg:px-8", className)}>{children}</div>;
}
