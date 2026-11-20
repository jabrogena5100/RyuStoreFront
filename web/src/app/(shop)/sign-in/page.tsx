import type { Metadata } from "next";
import { AuthForm } from "@/components/AuthForm";
import { AuthShell } from "@/components/AuthShell";
import { searchProducts } from "@/lib/api";

export const metadata: Metadata = { title: "Sign in" };

/** One product for the side panel; the page still renders if the API is down. */
async function panelProduct() {
  try {
    return (await searchProducts({ sort: "newest", pageSize: 1 })).items[0];
  } catch {
    return undefined;
  }
}

export default async function SignIn({ searchParams }: { searchParams: Promise<{ state?: string; next?: string }> }) {
  const { state, next } = await searchParams;
  return (
    <AuthShell mode="sign-in" product={await panelProduct()}>
      <AuthForm mode="sign-in" reviewState={state} next={next} />
    </AuthShell>
  );
}
