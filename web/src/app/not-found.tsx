import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/StatePanel";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <EmptyState title="That page is not here" action={<ButtonLink href="/products">Browse all shoes</ButtonLink>}>
        The link may be old, or the pair may have been removed.
      </EmptyState>
    </div>
  );
}
