import clsx from "clsx";
import { CircleAlert, CircleCheck, Info, TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";

const tones = {
  info: { icon: Info, box: "border-line bg-surface-2", color: "text-accent-text" },
  success: { icon: CircleCheck, box: "border-line bg-surface-2", color: "text-success" },
  warning: { icon: TriangleAlert, box: "border-line bg-surface-2", color: "text-warning" },
  danger: { icon: CircleAlert, box: "border-line bg-surface-2", color: "text-danger" },
} as const;

/** An inline message. Danger is announced at once (role alert); the rest are polite status messages. */
export function Alert({ tone = "info", title, children, action, className }: { tone?: keyof typeof tones; title: string; children?: ReactNode; action?: ReactNode; className?: string }) {
  const { icon: Icon, box, color } = tones[tone];
  return (
    <div role={tone === "danger" ? "alert" : "status"} className={clsx("flex gap-3 rounded-lg border p-4", box, className)}>
      <Icon aria-hidden="true" className={clsx("mt-0.5 size-5 shrink-0", color)} />
      <div className="min-w-0 flex-1">
        <p className="text-small font-medium text-fg-strong">{title}</p>
        {children ? <div className="mt-1 text-small text-fg-muted">{children}</div> : null}
        {action ? <div className="mt-3">{action}</div> : null}
      </div>
    </div>
  );
}
