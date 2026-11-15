import { Footer } from "@/components/Footer";
import { Header } from "@/components/Header";
import { ButtonLink } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { EmptyState } from "@/components/ui/StatePanel";

export default function NotFound() {
  return (
    <>
      <Header />
      <main id="main">
        <Container className="py-20">
          <EmptyState title="That page is not here" action={<ButtonLink href="/products">Browse all shoes</ButtonLink>}>
            The link may be old, or the pair may have been removed.
          </EmptyState>
        </Container>
      </main>
      <Footer />
    </>
  );
}
