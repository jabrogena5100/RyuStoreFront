import clsx from "clsx";
import type { ComponentProps, ReactNode } from "react";

export const controlClass = (error?: string) =>
  clsx(
    "min-h-11 w-full rounded-md border bg-surface-2 px-3.5 text-body text-fg placeholder:text-fg-subtle",
    "transition-colors duration-(--duration-base) disabled:opacity-50",
    error ? "border-danger" : "border-line-strong hover:border-fg-subtle",
  );

function Wrap({ id, label, hint, error, className, children }: { id: string; label: string; hint?: string; error?: string; className?: string; children: ReactNode }) {
  return (
    <div className={clsx("flex flex-col gap-1.5", className)}>
      <label htmlFor={id} className="text-small font-medium text-fg">
        {label}
      </label>
      {children}
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

const describe = (id: string, error?: string, hint?: string) => (error ? `${id}-error` : hint ? `${id}-hint` : undefined);

export function Field({ label, hint, error, id, className, ...props }: { label: string; hint?: string; error?: string; id: string } & ComponentProps<"input">) {
  return (
    <Wrap id={id} label={label} hint={hint} error={error} className={className}>
      <input id={id} aria-invalid={error ? true : undefined} aria-describedby={describe(id, error, hint)} className={controlClass(error)} {...props} />
    </Wrap>
  );
}

export function SelectField({ label, hint, error, id, className, children, ...props }: { label: string; hint?: string; error?: string; id: string } & ComponentProps<"select">) {
  return (
    <Wrap id={id} label={label} hint={hint} error={error} className={className}>
      <select id={id} aria-invalid={error ? true : undefined} aria-describedby={describe(id, error, hint)} className={controlClass(error)} {...props}>
        {children}
      </select>
    </Wrap>
  );
}

export function TextAreaField({ label, hint, error, id, className, ...props }: { label: string; hint?: string; error?: string; id: string } & ComponentProps<"textarea">) {
  return (
    <Wrap id={id} label={label} hint={hint} error={error} className={className}>
      <textarea id={id} aria-invalid={error ? true : undefined} aria-describedby={describe(id, error, hint)} className={clsx(controlClass(error), "py-2.5")} {...props} />
    </Wrap>
  );
}
