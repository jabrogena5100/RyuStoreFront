"use client";

import { Clock } from "lucide-react";
import { useEffect, useState } from "react";
import { fmtTime } from "@/lib/orders";

/**
 * Time left on the stock hold. `now` is for design review (a fixed clock so screenshots are stable);
 * without it the timer reads the real clock after the page loads and updates every 15 seconds.
 */
export function HoldTimer({ expiresAt, now }: { expiresAt: string | null; now?: string }) {
  const [clock, setClock] = useState<number | null>(now ? new Date(now).getTime() : null);
  useEffect(() => {
    if (now) return;
    const tick = () => setClock(Date.now());
    tick();
    const id = window.setInterval(tick, 15_000);
    return () => window.clearInterval(id);
  }, [now]);
  if (!expiresAt) return null;
  const end = new Date(expiresAt).getTime();
  const left = clock === null ? null : Math.max(0, Math.ceil((end - clock) / 60_000));
  return (
    <p className="flex items-center gap-2 text-small text-fg-muted">
      <Clock aria-hidden="true" className="size-4 shrink-0" />
      <span>
        Your pair is held until <span className="text-fg">{fmtTime(expiresAt)}</span>
        {left !== null ? <span className="tabular"> ({left} {left === 1 ? "minute" : "minutes"} left)</span> : null}
      </span>
    </p>
  );
}
