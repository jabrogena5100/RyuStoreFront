/*
 * Checkout steps. Adapted from "Stepper" by kevingirelli on 21st.dev (https://21st.dev/@kevingirelli/components/stepper),
 * licence shown there: MIT. Kept from the original: a <nav> landmark around an ordered list, aria-current="step" on the
 * current step, the state ("Completed", "Current", "Upcoming") read after each number because the check mark and colour are
 * not read, a line between steps that fills once a step is complete, and on phones only the current step showing its title.
 * Changed: no context or clickable triggers (the steps are pages, not tabs), no class-variance-authority, design tokens.
 * See design/COMPONENT-NOTES.md.
 */
import clsx from "clsx";
import { Check } from "lucide-react";

const STEPS = ["Your details", "Pay on Stripe", "Confirmation"] as const;
const STATE_TEXT = { complete: "Completed", current: "Current", upcoming: "Upcoming" } as const;

export function Steps({ current }: { current: 1 | 2 | 3 }) {
  return (
    <nav aria-label="Checkout progress">
      <ol className="flex items-center gap-2">
        {STEPS.map((title, i) => {
          const n = i + 1;
          const state = n < current ? "complete" : n === current ? "current" : "upcoming";
          const last = n === STEPS.length;
          return (
            <li key={title} aria-current={state === "current" ? "step" : undefined} className={clsx("flex items-center gap-2", !last && "flex-1", state === "current" && "min-w-0")}>
              <span
                className={clsx(
                  "grid size-8 shrink-0 place-items-center rounded-pill border font-mono text-small font-medium transition-colors duration-(--duration-base)",
                  state === "complete" && "border-accent bg-accent text-on-accent",
                  state === "current" && "border-accent bg-canvas text-accent-text",
                  state === "upcoming" && "border-line-control text-fg-muted",
                )}
              >
                {state === "complete" ? (
                  <>
                    <Check aria-hidden="true" className="size-4" />
                    <span className="sr-only">{n}</span>
                  </>
                ) : (
                  n
                )}
                <span className="sr-only">, {STATE_TEXT[state]}</span>
              </span>
              <span
                className={clsx(
                  "text-small font-medium",
                  state === "upcoming" ? "text-fg-muted" : "text-fg-strong",
                  state === "current" ? "max-sm:truncate" : "max-sm:sr-only",
                )}
              >
                {title}
              </span>
              {!last ? (
                <span aria-hidden="true" className={clsx("h-px min-w-4 flex-1 transition-colors duration-(--duration-base)", state === "complete" ? "bg-accent" : "bg-line-strong")} />
              ) : null}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
