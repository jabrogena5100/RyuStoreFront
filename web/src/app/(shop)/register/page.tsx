import type { Metadata } from "next";
import { AuthForm } from "@/components/AuthForm";
import { Container } from "@/components/ui/Container";

export const metadata: Metadata = { title: "Create account" };

export default async function Register({ searchParams }: { searchParams: Promise<{ state?: string; next?: string }> }) {
  const { state, next } = await searchParams;
  return (
    <Container className="py-10 sm:py-14">
      <AuthForm mode="register" state={state} next={next} />
    </Container>
  );
}
