import type { Metadata } from "next";
import { AuthForm } from "@/components/AuthForm";
import { AuthShell } from "@/components/AuthShell";
import { searchProducts } from "@/lib/api";

export const metadata: Metadata = { title: "Create account" };

/** One product for the side panel; the page still renders if the API is down. */
async function panelProduct() {
  try {
    return (await searchProducts({ sort: "newest", pageSize: 1 })).items[0];
  } catch {
    return undefined;
  }
}

export default async function Register({ searchParams }: { searchParams: Promise<{ state?: string; next?: string }> }) {
  const { state, next } = await searchParams;
  return (
    <AuthShell mode="register" product={await panelProduct()}>
      <AuthForm mode="register" reviewState={state} next={next} />
    </AuthShell>
  );
}
