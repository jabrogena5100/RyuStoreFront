import Link from "next/link";
import clsx from "clsx";
import type { ComponentProps, ReactNode } from "react";

type Variant = "primary" | "secondary" | "ghost";
type Size = "md" | "lg" | "icon";

const base =
  "inline-flex items-center justify-center gap-2 rounded-md font-medium select-none " +
  "transition-[background-color,color,opacity] duration-(--duration-base) ease-standard " +
  "disabled:pointer-events-none disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:opacity-50";

const variants: Record<Variant, string> = {
  primary: "bg-accent text-fg-strong hover:bg-accent-hover active:bg-accent-press",
  secondary: "bg-surface-2 text-fg border border-line hover:bg-surface-3",
  ghost: "text-fg-muted hover:text-fg hover:bg-surface-2",
};

const sizes: Record<Size, string> = {
  md: "min-h-11 px-4 text-small",
  lg: "min-h-13 px-6 text-body",
  icon: "size-11",
};

type Common = { variant?: Variant; size?: Size; className?: string; children: ReactNode };

export function Button({
  variant = "primary",
  size = "md",
  className,
  ...props
}: Common & Omit<ComponentProps<"button">, keyof Common>) {
  return <button type="button" className={clsx(base, variants[variant], sizes[size], className)} {...props} />;
}

export function ButtonLink({
  variant = "primary",
  size = "md",
  className,
  ...props
}: Common & Omit<ComponentProps<typeof Link>, keyof Common>) {
  return <Link className={clsx(base, variants[variant], sizes[size], className)} {...props} />;
}
