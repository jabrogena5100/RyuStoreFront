import type { ReactNode } from "react";
import { PackageSearch, TriangleAlert } from "lucide-react";

function Panel({ icon, title, children, action, role }: { icon: ReactNode; title: string; children: ReactNode; action?: ReactNode; role?: "alert" | "status" }) {
  return (
    <div role={role} className="mx-auto flex max-w-md flex-col items-center gap-3 rounded-lg border border-line bg-surface-1 px-6 py-12 text-center">
      <div className="grid size-12 place-items-center rounded-pill bg-surface-3 text-fg-muted">{icon}</div>
      <h2 className="text-h2 font-semibold text-fg-strong">{title}</h2>
      <p className="text-small text-fg-muted">{children}</p>
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}

export function EmptyState(props: { title: string; children: ReactNode; action?: ReactNode }) {
  return <Panel role="status" icon={<PackageSearch aria-hidden="true" className="size-6" />} {...props} />;
}

export function ErrorState(props: { title: string; children: ReactNode; action?: ReactNode }) {
  return <Panel role="alert" icon={<TriangleAlert aria-hidden="true" className="size-6 text-danger" />} {...props} />;
}
