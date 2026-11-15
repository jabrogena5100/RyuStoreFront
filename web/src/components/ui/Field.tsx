import clsx from "clsx";
import type { ComponentProps } from "react";

export function Field({
  label,
  hint,
  error,
  id,
  className,
  ...props
}: { label: string; hint?: string; error?: string; id: string } & ComponentProps<"input">) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className={clsx("flex flex-col gap-1.5", className)}>
      <label htmlFor={id} className="text-small font-medium text-fg">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={clsx(
          "min-h-11 rounded-md border bg-surface-2 px-3.5 text-body text-fg placeholder:text-fg-subtle",
          "transition-colors duration-(--duration-base) disabled:opacity-50",
          error ? "border-danger" : "border-line-strong hover:border-fg-subtle",
        )}
        {...props}
      />
      {error ? (
        <p id={`${id}-error`} className="text-small text-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-small text-fg-subtle">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
