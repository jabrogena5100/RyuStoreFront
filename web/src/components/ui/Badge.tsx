import clsx from "clsx";
import type { ReactNode } from "react";

const tones = {
  neutral: "bg-surface-3 text-fg",
  accent: "bg-accent-soft text-accent-text",
  success: "bg-surface-3 text-success",
  warning: "bg-surface-3 text-warning",
  danger: "bg-surface-3 text-danger",
} as const;

export function Badge({ tone = "neutral", children }: { tone?: keyof typeof tones; children: ReactNode }) {
  return (
    <span className={clsx("inline-flex items-center rounded-pill px-2.5 py-1 text-caption font-medium uppercase", tones[tone])}>
      {children}
    </span>
  );
}
