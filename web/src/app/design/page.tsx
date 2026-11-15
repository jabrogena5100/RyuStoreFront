import type { Metadata } from "next";
import { ProductCard } from "@/components/ProductCard";
import { ToastDemo } from "@/components/ToastDemo";
import { Badge } from "@/components/ui/Badge";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { Field } from "@/components/ui/Field";
import { Price } from "@/components/ui/Price";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/StatePanel";
import { PRODUCTS } from "@/lib/catalog";

export const metadata: Metadata = { title: "Design system" };

/** Contrast ratios are WCAG figures computed by script for the hex values in src/styles/tokens.css (see design/BRIEF.md). */
const SWATCHES: { token: string; bg: string; hex: string; role: string; note?: string }[] = [
  { token: "canvas", bg: "bg-canvas", hex: "#0B0A08", role: "Page background" },
  { token: "surface-1", bg: "bg-surface-1", hex: "#141211", role: "Cards, header" },
  { token: "surface-2", bg: "bg-surface-2", hex: "#1D1C1A", role: "Inputs, raised panels" },
  { token: "surface-3", bg: "bg-surface-3", hex: "#272624", role: "Hover, selected" },
  { token: "line", bg: "bg-line", hex: "#343331", role: "Dividers (decorative)" },
  { token: "line-strong", bg: "bg-line-strong", hex: "#71706D", role: "Control borders", note: "at least 3:1" },
  { token: "fg-subtle", bg: "bg-fg-subtle", hex: "#8F8D8B", role: "Captions", note: "6.0:1 on canvas" },
  { token: "fg-muted", bg: "bg-fg-muted", hex: "#A6A4A2", role: "Secondary text", note: "8.0:1" },
  { token: "fg", bg: "bg-fg", hex: "#E9E8E6", role: "Body text", note: "16.2:1" },
  { token: "fg-strong", bg: "bg-fg-strong", hex: "#FBFAF9", role: "Headings, button labels", note: "19.0:1" },
  { token: "accent", bg: "bg-accent", hex: "#1A6FE1", role: "Primary action", note: "4.6:1 with a label" },
  { token: "accent-hover", bg: "bg-accent-hover", hex: "#0462D3", role: "Hover", note: "5.5:1 with a label" },
  { token: "accent-press", bg: "bg-accent-press", hex: "#0054BD", role: "Pressed", note: "6.7:1 with a label" },
  { token: "accent-soft", bg: "bg-accent-soft", hex: "#132643", role: "Selected background" },
  { token: "accent-text", bg: "bg-accent-text", hex: "#5B90DD", role: "Links, focus", note: "6.1:1 on canvas" },
  { token: "success", bg: "bg-success", hex: "#64C897", role: "Success", note: "9.7:1" },
  { token: "warning", bg: "bg-warning", hex: "#E8BE62", role: "Warning", note: "11.3:1" },
  { token: "danger", bg: "bg-danger", hex: "#EF7E7C", role: "Error", note: "7.5:1" },
];

function Section({ id, title, children }: { id: string; title: string; children: React.ReactNode }) {
  return (
    <section aria-labelledby={id} className="border-t border-line py-10">
      <h2 id={id} className="mb-6 text-h1 font-semibold text-fg-strong">
        {title}
      </h2>
      {children}
    </section>
  );
}

export default function DesignPage() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <p className="text-caption font-medium uppercase text-accent-text">Design system</p>
      <h1 className="mt-2 text-display font-semibold text-fg-strong">Tokens and components</h1>
      <p className="mt-4 max-w-2xl text-body text-fg-muted">
        Everything in the storefront is built from the values on this page. They live in one file,{" "}
        <code className="rounded-sm bg-surface-2 px-1.5 py-0.5 text-small text-fg">src/styles/tokens.css</code>.
      </p>

      <Section id="colour" title="Colour">
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {SWATCHES.map((s) => (
            <li key={s.token} className="overflow-hidden rounded-lg border border-line bg-surface-1">
              <div className={`h-16 ${s.bg}`} />
              <div className="p-3">
                <p className="text-small font-medium text-fg-strong">{s.token}</p>
                <p className="tabular text-caption text-fg-subtle">{s.hex}</p>
                <p className="mt-1 text-caption text-fg-muted">{s.role}</p>
                {s.note ? <p className="text-caption text-fg-subtle">{s.note}</p> : null}
              </div>
            </li>
          ))}
        </ul>
      </Section>

      <Section id="type" title="Type">
        <div className="flex flex-col gap-5">
          <p className="text-display font-semibold text-fg-strong">Display: find the right pair</p>
          <p className="text-h1 font-semibold text-fg-strong">Heading 1: Chelsea boots for the late train</p>
          <p className="text-h2 font-semibold text-fg-strong">Heading 2: Shop by occasion</p>
          <p className="max-w-xl text-body text-fg">Body: Goodyear-welted so it can be resoled, with a leather lining and a cushioned footbed.</p>
          <p className="text-small text-fg-muted">Small: Flat-rate shipping per order. Sizes are US.</p>
          <p className="text-caption font-medium uppercase text-fg-subtle">Caption: Ostrava Atelier</p>
          <p className="tabular text-h2 font-medium text-fg-strong">Prices use tabular numbers: $145 $265 $1,295.50</p>
        </div>
      </Section>

      <Section id="buttons" title="Buttons">
        <div className="flex flex-col gap-6">
          <div className="flex flex-wrap items-center gap-3">
            <Button>Add to cart</Button>
            <Button variant="secondary">Save for later</Button>
            <Button variant="ghost">Clear filters</Button>
            <Button disabled>Sold out</Button>
            <ButtonLink href="/products" size="lg">Show 12 pairs</ButtonLink>
          </div>
          <p className="text-small text-fg-muted">Hover, pressed and focus states are live: tab to a button to see the focus ring. Minimum tap target is 44 px.</p>
        </div>
      </Section>

      <Section id="inputs" title="Inputs, chips and badges">
        <div className="grid gap-6 md:grid-cols-2">
          <div className="flex flex-col gap-4">
            <Field id="d-email" label="Email" type="email" placeholder="you@example.com" hint="We only use it for your receipt." />
            <Field id="d-postal" label="Postal code" defaultValue="9720" error="Enter a 5-digit ZIP code." />
            <Field id="d-off" label="Country" defaultValue="United States" disabled />
          </div>
          <div className="flex flex-col gap-6">
            <div className="flex flex-wrap gap-2">
              <Chip>Women</Chip>
              <Chip selected>Men</Chip>
              <Chip>White</Chip>
              <Chip selected>In stock</Chip>
            </div>
            <div className="flex flex-wrap gap-2">
              <Badge>Sold out</Badge>
              <Badge tone="accent">New</Badge>
              <Badge tone="success">In stock</Badge>
              <Badge tone="warning">Few left</Badge>
              <Badge tone="danger">Payment failed</Badge>
            </div>
            <div className="flex items-baseline gap-4">
              <Price minor={14500} className="text-h2" />
              <Price minor={129550} className="text-h2" />
              <Price minor={26500} prefix="from" className="text-body" />
            </div>
          </div>
        </div>
      </Section>

      <Section id="cards" title="Product cards">
        <ul className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-4">
          {[PRODUCTS[0], PRODUCTS[7], PRODUCTS[10], PRODUCTS[14]].map((p) => (
            <li key={p.id}>
              <ProductCard product={p} />
            </li>
          ))}
        </ul>
        <p className="mt-4 text-small text-fg-muted">
          The pictures are placeholders on purpose: one 4:5 ratio, tone on tone, an oversized initial of the model name that differs per product and per image. They do not pretend to be photos; real photos drop into the same frame.
        </p>
      </Section>

      <Section id="states" title="Loading, empty, error">
        <div className="grid gap-6 md:grid-cols-3">
          <div className="flex flex-col gap-3">
            <Skeleton className="aspect-[4/5] w-full rounded-lg" />
            <Skeleton className="h-3 w-16" />
            <Skeleton className="h-5 w-3/4" />
          </div>
          <EmptyState title="No pairs match these filters" action={<Button variant="secondary">Clear filters</Button>}>
            Try another size or occasion.
          </EmptyState>
          <ErrorState title="We could not load the shoes" action={<Button>Try again</Button>}>
            Nothing is lost. Try again in a moment.
          </ErrorState>
        </div>
      </Section>

      <Section id="motion" title="Motion and elevation">
        <div className="grid gap-6 md:grid-cols-2">
          <div>
            <ul className="space-y-2 text-small text-fg-muted">
              <li>Durations: 120 ms (fast), 200 ms (base), 320 ms (slow), 500 ms (slower).</li>
              <li>Easings: standard <code className="text-fg">cubic-bezier(0.2, 0, 0, 1)</code> and emphasised <code className="text-fg">cubic-bezier(0.16, 1, 0.3, 1)</code>.</li>
              <li>Only transform and opacity animate. Reduced motion turns every animation into a still change.</li>
              <li>Depth is a lighter surface plus a line; only overlays (menu, toast) cast a shadow.</li>
            </ul>
            <div className="mt-4">
              <ToastDemo />
            </div>
          </div>
          <div className="rounded-lg border border-line bg-surface-1 p-5">
            <div className="flex gap-3">
              <div className="grid h-20 flex-1 place-items-center rounded-md bg-surface-2 text-small text-fg-muted">surface-2</div>
              <div className="grid h-20 flex-1 place-items-center rounded-md bg-surface-3 text-small text-fg-muted">surface-3</div>
              <div className="grid h-20 flex-1 place-items-center rounded-md border border-line bg-surface-3 text-small text-fg-muted shadow-overlay">overlay</div>
            </div>
          </div>
        </div>
      </Section>
    </div>
  );
}
