import clsx from "clsx";
import { formatPrice } from "@/lib/catalog";

export function Price({
  minor,
  currency = "USD",
  prefix,
  className,
}: {
  minor: number;
  currency?: string;
  prefix?: string;
  className?: string;
}) {
  return (
    <span className={clsx("tabular font-mono font-medium tracking-tight text-fg-strong", className)}>
      {prefix ? <span className="mr-1 font-normal text-fg-subtle">{prefix}</span> : null}
      {formatPrice(minor, currency)}
    </span>
  );
}
