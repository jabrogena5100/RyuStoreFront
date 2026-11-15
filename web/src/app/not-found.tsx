import { Container } from "@/components/ui/Container";
import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/StatePanel";

export default function NotFound() {
  return (
    <Container className="py-20">
      <EmptyState title="That page is not here" action={<ButtonLink href="/products">Browse all shoes</ButtonLink>}>
        The link may be old, or the pair may have been removed.
      </EmptyState>
    </Container>
  );
}
