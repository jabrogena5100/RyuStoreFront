import Link from "next/link";
import clsx from "clsx";
import { Check } from "lucide-react";
import { ALL_SIZES, OCCASIONS, PRICE_BANDS, countWith, type ListingQuery } from "@/lib/catalog";
import { listingHref } from "@/lib/listing";

/*
 * Filter panel for the listing: one panel, used as the desktop sidebar and inside the phone drawer.
 * Every option is a link to a URL (server-rendered, works without scripts, shareable), with the number of pairs
 * it would show given the other filters. Layout pattern (grouped options with counts at the right edge) follows
 * a 21st.dev category page (MIT); the code is our own, on native elements. See docs/decisions.md.
 */

function Option({ href, selected, label, count, kind }: { href: string; selected: boolean; label: string; count: number; kind: "radio" | "check" }) {
  const disabled = count === 0 && !selected;
  const box = clsx(
    "grid size-5 shrink-0 place-items-center border",
    kind === "radio" ? "rounded-pill" : "rounded-sm",
    selected ? "border-accent bg-accent text-fg-strong" : "border-line-strong",
  );
  const inner = (
    <>
      <span aria-hidden="true" className={box}>
        {selected ? <Check className="size-3.5" /> : null}
      </span>
      <span className={clsx("flex-1", disabled ? "text-fg-subtle" : "text-fg")}>{label}</span>
      <span className="tabular text-fg-subtle">{count}</span>
    </>
  );
  const row = "flex min-h-11 items-center gap-3 rounded-md px-2 text-small";
  return disabled ? (
    <span aria-disabled="true" className={row}>{inner}</span>
  ) : (
    <Link
      href={href}
      scroll={false}
      aria-current={selected ? "true" : undefined}
      className={clsx(row, "transition-colors duration-(--duration-base) hover:bg-surface-2")}
    >
      {inner}
    </Link>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="border-b border-line py-5 first:pt-0 last:border-b-0">
      <h3 className="mb-2 px-2 text-caption font-medium uppercase text-fg-subtle">{title}</h3>
      {children}
    </section>
  );
}

export function ListingFilters({ q }: { q: ListingQuery }) {
  const toggle = <K extends keyof ListingQuery>(key: K, value: string) => listingHref(q, { [key]: q[key] === value ? undefined : value });
  return (
    <div role="group" aria-label="Filters">
      <Group title="Occasion">
        <ul>
          {OCCASIONS.map((o) => (
            <li key={o.slug}>
              <Option kind="radio" href={toggle("occasion", o.slug)} selected={q.occasion === o.slug} label={o.label} count={countWith(q, { occasion: o.slug })} />
            </li>
          ))}
        </ul>
      </Group>
      <Group title="Who it is for">
        <ul>
          {(["women", "men"] as const).map((g) => (
            <li key={g}>
              <Option kind="radio" href={toggle("gender", g)} selected={q.gender === g} label={g === "women" ? "Women" : "Men"} count={countWith(q, { gender: g })} />
            </li>
          ))}
        </ul>
      </Group>
      <Group title="Price">
        <ul>
          {PRICE_BANDS.map((b) => (
            <li key={b.value}>
              <Option kind="radio" href={toggle("price", b.value)} selected={q.price === b.value} label={b.label} count={countWith(q, { price: b.value })} />
            </li>
          ))}
        </ul>
      </Group>
      <Group title="Size (US)">
        <ul className="grid grid-cols-4 gap-2 px-2">
          {ALL_SIZES.map((s) => {
            const selected = q.size === s;
            const n = countWith(q, { size: s });
            const cls = clsx(
              "tabular grid min-h-11 place-items-center rounded-md border text-small font-medium",
              selected ? "border-accent-line bg-accent-soft text-accent-text" : n === 0 ? "border-line text-fg-subtle line-through" : "border-line-strong text-fg",
            );
            return (
              <li key={s}>
                {n === 0 && !selected ? (
                  <span aria-disabled="true" aria-label={`US ${s}, no pairs`} className={cls}>{s}</span>
                ) : (
                  <Link
                    href={toggle("size", s)}
                    scroll={false}
                    aria-current={selected ? "true" : undefined}
                    aria-label={`US ${s}, ${n} ${n === 1 ? "pair" : "pairs"}`}
                    className={clsx(cls, "transition-colors duration-(--duration-base) hover:bg-surface-2")}
                  >
                    {s}
                  </Link>
                )}
              </li>
            );
          })}
        </ul>
      </Group>
      <Group title="Colour">
        <Option kind="check" href={toggle("colour", "white")} selected={q.colour === "white"} label="White and off-white" count={countWith(q, { colour: "white" })} />
      </Group>
      <Group title="Availability">
        <Option kind="check" href={toggle("stock", "1")} selected={q.stock === "1"} label="In stock" count={countWith(q, { stock: "1" })} />
      </Group>
    </div>
  );
}
