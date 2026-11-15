"use client";

import { SlidersHorizontal, X } from "lucide-react";
import { useRef, type ReactNode } from "react";

/** Phone filter drawer: a native modal <dialog> (focus trap, Escape and backdrop come from the browser). The panel inside is server-rendered. */
export function FilterDrawer({ activeCount, resultLabel, children }: { activeCount: number; resultLabel: string; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  return (
    <>
      <button
        type="button"
        onClick={() => ref.current?.showModal()}
        className="inline-flex min-h-11 items-center gap-2 rounded-md border border-line-strong bg-surface-2 px-4 text-small font-medium text-fg transition-colors duration-(--duration-base) hover:bg-surface-3 lg:hidden"
      >
        <SlidersHorizontal aria-hidden="true" className="size-4" />
        Filters
        {activeCount > 0 ? (
          <span className="tabular grid min-w-5 place-items-center rounded-pill bg-accent px-1.5 text-caption font-semibold text-fg-strong">
            <span className="sr-only">{activeCount} active</span>
            <span aria-hidden="true">{activeCount}</span>
          </span>
        ) : null}
      </button>
      <dialog
        ref={ref}
        aria-label="Filters"
        onClick={(e) => {
          if (e.target === ref.current) ref.current?.close();
        }}
        className="m-0 ml-auto h-dvh w-[min(24rem,100vw)] flex-col border-l border-line bg-surface-1 p-0 text-fg shadow-overlay open:flex"
      >
        <div className="flex items-center justify-between border-b border-line px-4 py-2">
          <h2 className="text-h2 font-semibold text-fg-strong">Filters</h2>
          <button type="button" aria-label="Close filters" onClick={() => ref.current?.close()} className="grid size-11 place-items-center rounded-md hover:bg-surface-3">
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-3 py-5">{children}</div>
        <div className="border-t border-line p-4">
          <button
            type="button"
            onClick={() => ref.current?.close()}
            className="min-h-12 w-full rounded-md bg-accent px-6 text-body font-medium text-fg-strong transition-colors duration-(--duration-base) hover:bg-accent-hover"
          >
            {resultLabel}
          </button>
        </div>
      </dialog>
    </>
  );
}
