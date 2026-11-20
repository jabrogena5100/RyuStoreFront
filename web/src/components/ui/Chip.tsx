import Link from "next/link";
import clsx from "clsx";
import type { ComponentProps, ReactNode } from "react";

const base =
  "inline-flex min-h-11 shrink-0 items-center gap-2 rounded-pill border px-4 text-small font-medium whitespace-nowrap " +
  "transition-[background-color,border-color,color] duration-(--duration-base) ease-standard";
const idle = "border-line-control bg-transparent text-fg-muted hover:bg-surface-2 hover:text-fg";
const on = "border-accent bg-accent text-on-accent";

type Common = { selected?: boolean; className?: string; children: ReactNode };

export function Chip({ selected, className, ...props }: Common & Omit<ComponentProps<"button">, keyof Common>) {
  return <button type="button" aria-pressed={selected} className={clsx(base, selected ? on : idle, className)} {...props} />;
}

export function ChipLink({ selected, className, ...props }: Common & Omit<ComponentProps<typeof Link>, keyof Common>) {
  return <Link aria-current={selected ? "true" : undefined} className={clsx(base, selected ? on : idle, className)} {...props} />;
}
