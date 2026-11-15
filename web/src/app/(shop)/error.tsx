"use client";

import { Button } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { ErrorState } from "@/components/ui/StatePanel";

/** Anything that throws while a shop page renders (the API is down, say) lands here instead of a blank page. */
export default function ShopError({ reset }: { error: Error; reset: () => void }) {
  return (
    <Container className="py-16">
      <ErrorState title="Something went wrong on our side" action={<Button onClick={reset}>Try again</Button>}>
        Nothing is lost. Try again in a moment.
      </ErrorState>
    </Container>
  );
}
