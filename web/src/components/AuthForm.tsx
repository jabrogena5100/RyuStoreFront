import Link from "next/link";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";

type Mode = "sign-in" | "register";

/** Sign in and create account share one form. `state` is for design review: idle, submitting, invalid (wrong credentials), taken (e-mail in use), error (network or server). */
export function AuthForm({ mode, state, next }: { mode: Mode; state?: string; next?: string }) {
  const register = mode === "register";
  const busy = state === "submitting";
  const checkout = next === "checkout";
  return (
    <div className="mx-auto w-full max-w-md">
      <h1 className="text-h1 font-semibold text-fg-strong">{register ? "Create your account" : "Sign in"}</h1>
      <p className="mt-2 text-small text-fg-muted">
        {checkout ? "You need an account to pay. Your bag comes with you." : register ? "Save your orders and check out in a few taps." : "Welcome back."}
      </p>

      <form className="mt-6 flex flex-col gap-5 rounded-lg border border-line bg-surface-1 p-5 sm:p-6" noValidate aria-busy={busy || undefined}>
        {state === "invalid" ? (
          <Alert tone="danger" title="That e-mail and password do not match">
            Check both and try again.
          </Alert>
        ) : null}
        {state === "error" ? (
          <Alert tone="danger" title="We could not reach the server">
            Nothing was sent. Check your connection and try again.
          </Alert>
        ) : null}
        {register ? <Field id="displayName" name="displayName" label="Name (optional)" autoComplete="name" defaultValue={busy ? "Ada Buyer" : ""} disabled={busy} /> : null}
        <Field
          id="email"
          name="email"
          type="email"
          label="E-mail"
          autoComplete="email"
          defaultValue={state === "taken" || busy || state === "invalid" ? "ada@example.com" : ""}
          error={state === "taken" ? "An account with this e-mail already exists." : undefined}
          disabled={busy}
        />
        {state === "taken" ? (
          <p className="-mt-3 text-small text-fg-muted">
            <Link href="/sign-in" className="text-accent-text underline-offset-4 hover:underline">Sign in instead</Link>
          </p>
        ) : null}
        <Field
          id="password"
          name="password"
          type="password"
          label="Password"
          autoComplete={register ? "new-password" : "current-password"}
          hint={register ? "10 to 72 characters." : undefined}
          defaultValue={busy ? "correct horse battery" : ""}
          disabled={busy}
        />
        <Button type="submit" size="lg" disabled={busy} aria-disabled={busy || undefined} className="w-full">
          {busy ? (register ? "Creating account" : "Signing in") : register ? "Create account" : "Sign in"}
        </Button>
      </form>

      <p className="mt-5 text-small text-fg-muted">
        {register ? "Already have an account? " : "New here? "}
        <Link href={register ? "/sign-in" : "/register"} className="font-medium text-accent-text underline-offset-4 hover:underline">
          {register ? "Sign in" : "Create an account"}
        </Link>
      </p>
    </div>
  );
}
