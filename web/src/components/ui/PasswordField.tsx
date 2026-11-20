"use client";

import { Eye, EyeOff } from "lucide-react";
import { useState, type ComponentProps } from "react";
import { controlClass } from "@/components/ui/Field";

/** A password input with a show/hide toggle (pattern from the 21st.dev "Split Sign In Form"). The toggle is a real button with a pressed state. */
export function PasswordField({ id, label, hint, error, ...props }: { id: string; label: string; hint?: string; error?: string } & Omit<ComponentProps<"input">, "type">) {
  const [shown, setShown] = useState(false);
  const described = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-small font-medium text-fg">
        {label}
      </label>
      <div className="relative">
        <input id={id} type={shown ? "text" : "password"} aria-invalid={error ? true : undefined} aria-describedby={described} className={`${controlClass(error)} pr-12`} {...props} />
        <button
          type="button"
          aria-label="Show password"
          aria-pressed={shown}
          onClick={() => setShown((s) => !s)}
          className="absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-md text-fg-muted transition-colors duration-(--duration-base) hover:text-fg"
        >
          {shown ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}
        </button>
      </div>
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
