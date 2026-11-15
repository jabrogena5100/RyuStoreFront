"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "@/components/AuthProvider";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { ApiClientError } from "@/lib/api-client";

type Mode = "sign-in" | "register";
type Status = "idle" | "submitting" | "invalid" | "error";

/** Messages for the field codes in docs/api/auth.md. */
const FIELD_TEXT: Record<string, Record<string, string>> = {
  email: {
    required: "Enter your e-mail.",
    length: "E-mails can be up to 100 characters.",
    invalid_email: "Enter a valid e-mail address (letters, numbers and common symbols only).",
    taken: "An account with this e-mail already exists.",
  },
  password: { required: "Enter a password.", length: "Use 10 to 72 characters.", too_long: "That password is too long (72 bytes at most)." },
  displayName: { length: "Names can be up to 80 characters." },
};

/** Only same-site paths: anything else (another origin, a protocol-relative URL) falls back to the home page. */
export function safeNext(next?: string): string {
  if (!next) return "/";
  const path = next === "checkout" ? "/checkout" : next;
  return path.startsWith("/") && !path.startsWith("//") && !path.includes("\\") ? path : "/";
}

/**
 * Sign in and create account. `reviewState` is for design review only (idle, submitting, invalid, taken, error): it shows that state
 * on load; the form is live either way.
 */
export function AuthForm({ mode, reviewState, next }: { mode: Mode; reviewState?: string; next?: string }) {
  const register = mode === "register";
  const router = useRouter();
  const { customer, signIn, register: signUp } = useAuth();
  const [status, setStatus] = useState<Status>(reviewState === "submitting" ? "submitting" : reviewState === "invalid" ? "invalid" : reviewState === "error" ? "error" : "idle");
  const [fields, setFields] = useState<Record<string, string>>(reviewState === "taken" ? { email: "taken" } : {});
  const busy = status === "submitting";
  const target = safeNext(next);

  // Someone who is already signed in has nothing to do here.
  useEffect(() => {
    if (customer && !reviewState) router.replace(target);
  }, [customer, reviewState, router, target]);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const email = String(f.get("email") ?? "").trim();
    const password = String(f.get("password") ?? "");
    setStatus("submitting");
    setFields({});
    try {
      if (register) await signUp(email, password, String(f.get("displayName") ?? "").trim() || undefined);
      else await signIn(email, password);
      router.push(target);
      router.refresh();
    } catch (err) {
      if (err instanceof ApiClientError && err.status === 401) setStatus("invalid");
      else if (err instanceof ApiClientError && err.code === "email_taken") {
        setFields({ email: "taken" });
        setStatus("idle");
      } else if (err instanceof ApiClientError && err.code === "validation_failed") {
        setFields(err.fields);
        setStatus("idle");
      } else setStatus("error");
    }
  }

  const text = (name: string) => (fields[name] ? (FIELD_TEXT[name]?.[fields[name]] ?? "Check this field.") : undefined);
  const stuck = reviewState === "submitting";
  const sample = reviewState === "taken" || reviewState === "submitting" || reviewState === "invalid";

  return (
    <div className="mx-auto w-full max-w-md">
      <h1 className="text-h1 font-semibold text-fg-strong">{register ? "Create your account" : "Sign in"}</h1>
      <p className="mt-2 text-small text-fg-muted">
        {next === "checkout" || next === "/checkout" ? "You need an account to pay. Your bag comes with you." : register ? "Save your orders and check out in a few taps." : "Welcome back."}
      </p>

      <form onSubmit={submit} className="mt-6 flex flex-col gap-5 rounded-lg border border-line bg-surface-1 p-5 sm:p-6" noValidate aria-busy={busy || undefined}>
        {status === "invalid" ? (
          <Alert tone="danger" title="That e-mail and password do not match">
            Check both and try again.
          </Alert>
        ) : null}
        {status === "error" ? (
          <Alert tone="danger" title="We could not reach the server">
            Nothing was sent. Check your connection and try again.
          </Alert>
        ) : null}
        {register ? <Field id="displayName" name="displayName" label="Name (optional)" autoComplete="name" defaultValue={stuck ? "Ada Buyer" : ""} error={text("displayName")} disabled={busy} /> : null}
        <Field id="email" name="email" type="email" label="E-mail" autoComplete="email" defaultValue={sample ? "ada@example.com" : ""} error={text("email")} disabled={busy} />
        {fields.email === "taken" ? (
          <p className="-mt-3 text-small text-fg-muted">
            <Link href="/sign-in" className="text-accent-text underline underline-offset-4">Sign in instead</Link>
          </p>
        ) : null}
        <Field
          id="password"
          name="password"
          type="password"
          label="Password"
          autoComplete={register ? "new-password" : "current-password"}
          hint={register ? "10 to 72 characters." : undefined}
          defaultValue={stuck ? "correct horse battery" : ""}
          error={text("password")}
          disabled={busy}
        />
        <Button type="submit" size="lg" disabled={busy} aria-disabled={busy || undefined} className="w-full">
          {busy ? (register ? "Creating account" : "Signing in") : register ? "Create account" : "Sign in"}
        </Button>
      </form>

      <p className="mt-5 text-small text-fg-muted">
        {register ? "Already have an account? " : "New here? "}
        <Link href={`${register ? "/sign-in" : "/register"}${next ? `?next=${encodeURIComponent(next)}` : ""}`} className="font-medium text-accent-text underline underline-offset-4">
          {register ? "Sign in" : "Create an account"}
        </Link>
      </p>
    </div>
  );
}
