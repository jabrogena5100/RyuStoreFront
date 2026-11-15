import clsx from "clsx";

/** The three steps from cart to done. Current step is marked with aria-current; no colour-only meaning. */
export function Steps({ current }: { current: 1 | 2 | 3 }) {
  const steps = ["Your details", "Pay on Stripe", "Confirmation"];
  return (
    <ol aria-label="Checkout steps" className="flex flex-wrap items-center gap-x-3 gap-y-1 text-small">
      {steps.map((s, i) => {
        const n = i + 1;
        return (
          <li key={s} aria-current={n === current ? "step" : undefined} className="flex items-center gap-3">
            <span className={clsx("flex items-center gap-2", n === current ? "font-medium text-fg-strong" : "text-fg-subtle")}>
              <span aria-hidden="true" className={clsx("tabular grid size-6 place-items-center rounded-pill text-caption font-semibold", n === current ? "bg-accent text-fg-strong" : n < current ? "bg-surface-3 text-fg" : "border border-line-strong text-fg-subtle")}>
                {n}
              </span>
              {s}
            </span>
            {n < steps.length ? <span aria-hidden="true" className="h-px w-6 bg-line-strong sm:w-10" /> : null}
          </li>
        );
      })}
    </ol>
  );
}
