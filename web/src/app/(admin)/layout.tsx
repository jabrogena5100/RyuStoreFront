import Link from "next/link";
import { AdminNav } from "@/components/admin/AdminNav";
import { Container } from "@/components/ui/Container";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <header className="border-b border-line bg-surface-1">
        <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-surface-3 focus:px-4 focus:py-3">
          Skip to content
        </a>
        <Container className="flex min-h-14 items-center justify-between gap-4 py-2">
          <p className="text-body font-semibold tracking-tight text-fg-strong">
            Storefront<span className="text-accent-text">.</span> <span className="ml-1 rounded-sm bg-surface-3 px-2 py-0.5 text-caption font-medium uppercase text-fg-muted">Admin</span>
          </p>
          <div className="flex items-center gap-1 text-small">
            <Link href="/" className="inline-flex min-h-11 items-center rounded-md px-3 text-fg-muted hover:bg-surface-2 hover:text-fg">View shop</Link>
            <span className="hidden text-fg-subtle sm:inline">Ada Admin</span>
          </div>
        </Container>
      </header>
      <Container className="grid gap-6 py-6 lg:grid-cols-[11rem_minmax(0,1fr)] lg:gap-10 lg:py-8">
        <AdminNav />
        <main id="main" className="min-w-0">{children}</main>
      </Container>
    </>
  );
}
