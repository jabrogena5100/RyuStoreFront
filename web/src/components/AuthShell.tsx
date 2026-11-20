import { Check } from "lucide-react";
import type { ReactNode } from "react";
import { ProductImage } from "@/components/ProductImage";
import { Container } from "@/components/ui/Container";
import type { ProductSummary } from "@/lib/catalog";

const PERKS = {
  "sign-in": ["Your bag is waiting where you left it", "Orders and their status in one place", "Check out in two steps"],
  register: ["A bag that follows you between devices", "Every order and its history", "Pay on Stripe's page: card data never reaches this shop"],
} as const;

/**
 * Split-screen frame for sign in and create account. Pattern from the 21st.dev shortlist ("Split Sign In Form" and
 * "Sign Up Split Panel" by diarmuradi). Adapted: the testimonial is replaced by a real catalogue product and true
 * statements about the shop (there are no customers to quote); no social sign-in (the API has none); the panel is hidden
 * on phones so the form is first.
 */
export function AuthShell({ mode, product, children }: { mode: "sign-in" | "register"; product?: ProductSummary; children: ReactNode }) {
  return (
    <Container className="py-8 sm:py-12">
      <div className="grid overflow-hidden rounded-lg border border-line lg:min-h-[40rem] lg:grid-cols-[1fr_1.05fr]">
        <div className="flex items-center bg-canvas px-5 py-10 sm:px-12 lg:py-14">{children}</div>
        <aside aria-label="About your account" className="relative hidden bg-surface-1 lg:block">
          {product ? (
            <div className="absolute inset-0">
              <ProductImage product={product} view={2} label={false} decorative className="aspect-auto! h-full" />
            </div>
          ) : null}
          <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-t from-canvas via-canvas/60 to-transparent" />
          <div className="absolute inset-x-0 bottom-0 p-10">
            <p className="font-mono text-caption uppercase text-accent-text">{mode === "register" ? "With an account" : "Your account"}</p>
            <ul className="mt-4 flex flex-col gap-3">
              {PERKS[mode].map((p) => (
                <li key={p} className="flex items-start gap-3 text-body text-fg">
                  <Check aria-hidden="true" className="mt-1 size-4 shrink-0 text-accent-text" />
                  {p}
                </li>
              ))}
            </ul>
            <p className="mt-8 border-t border-line pt-4 font-mono text-caption uppercase text-fg-muted">
              Demo store · Stripe test mode · card 4242 4242 4242 4242
            </p>
          </div>
        </aside>
      </div>
    </Container>
  );
}
