import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/StatePanel";

/** A customer who reaches an admin page gets 403 forbidden (docs/api/auth.md): say so, do not show a blank page. */
export function Forbidden() {
  return (
    <EmptyState title="You do not have access to this area" action={<ButtonLink href="/">Back to the shop</ButtonLink>}>
      This part of the site is for shop staff. If you should have access, ask an administrator to change your role, then sign in again.
    </EmptyState>
  );
}
