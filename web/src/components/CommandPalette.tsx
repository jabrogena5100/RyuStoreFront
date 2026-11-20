"use client";

/*
 * Search palette (Cmd/Ctrl+K). Adapted from "Command Palette" by vovalopuh13 on 21st.dev
 * (https://21st.dev/@vovalopuh13/components/command-palette), licence shown there: MIT. Kept from the original: the fuzzy
 * subsequence match and its scoring, matched letters underlined, grouped results, combobox + listbox semantics with
 * aria-activedescendant, Up/Down/Home/End/Enter/Escape, a preview pane for the active row, the result count and key hints.
 * Changed: a native <dialog> (focus trap, inert page and Escape from the browser) instead of a hand-built overlay; the
 * framer-motion springs, cursor glow, 3D tilt and backdrop blur are removed (a 180 ms fade, none under reduced motion);
 * products come from GET /api/products; design tokens. See design/COMPONENT-NOTES.md.
 */
import { useRouter } from "next/navigation";
import { ArrowDown, ArrowUp, CornerDownLeft, Search } from "lucide-react";
import { useCallback, useEffect, useId, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { ProductImage } from "@/components/ProductImage";
import { Price } from "@/components/ui/Price";
import { OCCASIONS, type ProductSummary } from "@/lib/catalog";

type Item = {
  id: string;
  group: "Products" | "Occasions" | "Browse";
  title: string;
  subtitle?: string;
  href: string;
  product?: ProductSummary;
};

const STATIC: Item[] = [
  ...OCCASIONS.map((o) => ({ id: `occ-${o.slug}`, group: "Occasions" as const, title: o.label, subtitle: o.blurb, href: `/products?occasion=${o.slug}` })),
  { id: "b-women", group: "Browse", title: "Women", href: "/products?gender=women" },
  { id: "b-men", group: "Browse", title: "Men", href: "/products?gender=men" },
  { id: "b-new", group: "Browse", title: "New in", href: "/products?sort=newest" },
  { id: "b-150", group: "Browse", title: "Under $150", href: "/products?price=u150" },
];

/** Subsequence match: the matched indexes and a score (lower is better), or null. From the original component. */
function fuzzy(text: string, q: string): { idx: number[]; score: number } | null {
  if (!q) return { idx: [], score: 0 };
  const t = text.toLowerCase();
  const s = q.toLowerCase();
  const at = t.indexOf(s);
  if (at >= 0) return { idx: Array.from({ length: s.length }, (_, i) => at + i), score: at * 0.5 };
  const idx: number[] = [];
  let j = 0;
  for (let i = 0; i < t.length && j < s.length; i++) if (t[i] === s[j]) (idx.push(i), j++);
  if (j < s.length) return null;
  return { idx, score: 20 + idx[idx.length - 1] - idx[0] };
}

function Lit({ text, idx }: { text: string; idx: number[] }) {
  if (!idx.length) return <>{text}</>;
  const set = new Set(idx);
  return (
    <>
      {Array.from(text).map((c, i) =>
        set.has(i) ? (
          <span key={i} className="font-semibold text-fg-strong underline decoration-accent decoration-2 underline-offset-[3px]">
            {c}
          </span>
        ) : (
          c
        ),
      )}
    </>
  );
}

function Key({ children }: { children: ReactNode }) {
  return (
    <kbd className="grid h-6 min-w-6 place-items-center rounded-sm border border-line-control px-1.5 font-mono text-caption text-fg-muted">
      {children}
    </kbd>
  );
}

export function CommandPalette() {
  const router = useRouter();
  const uid = useId();
  const dialog = useRef<HTMLDialogElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLUListElement>(null);
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const [active, setActive] = useState(0);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [mac, setMac] = useState(false);

  useEffect(() => setMac(/Mac|iPhone|iPad/.test(navigator.platform)), []);

  const show = useCallback(() => {
    dialog.current?.showModal();
    setOpen(true);
  }, []);
  const close = useCallback(() => dialog.current?.close(), []);

  // Cmd/Ctrl+K from anywhere, and "/" when not typing in a field.
  useEffect(() => {
    const h = (e: globalThis.KeyboardEvent) => {
      const typing = e.target instanceof HTMLElement && (e.target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName));
      if (((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") || (e.key === "/" && !typing)) {
        e.preventDefault();
        if (dialog.current?.open) close();
        else show();
      }
    };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [show, close]);

  // Products from the real search, debounced; the newest pairs before anything is typed.
  useEffect(() => {
    if (!open) return;
    const ctrl = new AbortController();
    const t = window.setTimeout(() => {
      const params = new URLSearchParams({ pageSize: "6" });
      if (q.trim()) params.set("q", q.trim());
      else params.set("sort", "newest");
      fetch(`/api/products?${params}`, { signal: ctrl.signal })
        .then((r) => (r.ok ? r.json() : { items: [] }))
        .then((d: { items: ProductSummary[] }) => setProducts(d.items ?? []))
        .catch(() => {});
    }, 150);
    return () => {
      window.clearTimeout(t);
      ctrl.abort();
    };
  }, [q, open]);

  const groups = useMemo(() => {
    const productItems: Item[] = products.map((p) => ({ id: `p-${p.slug}`, group: "Products", title: p.name, subtitle: `${p.brand} · ${p.categoryName}`, href: `/products/${p.slug}`, product: p }));
    if (q.trim()) productItems.push({ id: "all", group: "Products", title: `Search all shoes for “${q.trim()}”`, href: `/products?q=${encodeURIComponent(q.trim())}` });
    const scored = [
      // The API already matched products on text; keep its order and only underline letters.
      ...productItems.map((it) => ({ it, idx: fuzzy(it.title, q)?.idx ?? [], score: 0 })),
      ...STATIC.map((it) => {
        const m = fuzzy(it.title, q) ?? (q ? fuzzy(`${it.subtitle ?? ""}`, q) : null);
        return m ? { it, idx: fuzzy(it.title, q)?.idx ?? [], score: m.score } : null;
      }).filter((x): x is { it: Item; idx: number[]; score: number } => x !== null),
    ];
    const order: Item["group"][] = ["Products", "Occasions", "Browse"];
    return order
      .map((name) => ({ name, rows: scored.filter((r) => r.it.group === name).sort((a, b) => a.score - b.score) }))
      .filter((g) => g.rows.length);
  }, [products, q]);

  const flat = groups.flatMap((g) => g.rows);
  const cur = flat[Math.min(active, flat.length - 1)];

  useEffect(() => setActive(0), [q]);
  useEffect(() => {
    list.current?.querySelector(`[data-i="${active}"]`)?.scrollIntoView({ block: "nearest" });
  }, [active]);

  function go(it?: Item) {
    if (!it) return;
    close();
    router.push(it.href);
  }

  function onKey(e: KeyboardEvent) {
    const n = Math.max(1, flat.length);
    if (e.key === "ArrowDown") (e.preventDefault(), setActive((a) => (a + 1) % n));
    else if (e.key === "ArrowUp") (e.preventDefault(), setActive((a) => (a - 1 + n) % n));
    else if (e.key === "Home") (e.preventDefault(), setActive(0));
    else if (e.key === "End") (e.preventDefault(), setActive(n - 1));
    else if (e.key === "Enter") (e.preventDefault(), go(cur?.it));
  }

  let counter = -1;
  return (
    <>
      <button
        type="button"
        onClick={show}
        aria-haspopup="dialog"
        aria-keyshortcuts="Meta+K Control+K"
        className="flex min-h-11 w-full min-w-0 items-center gap-3 rounded-md border border-line-control bg-surface-1 px-3.5 text-left text-small text-fg-muted transition-colors duration-(--duration-base) hover:text-fg"
      >
        <Search aria-hidden="true" className="size-4 shrink-0" />
        <span className="flex-1 truncate">Search shoes</span>
        <span aria-hidden="true" className="hidden gap-1 sm:flex">
          <Key>{mac ? "⌘" : "Ctrl"}</Key>
          <Key>K</Key>
        </span>
      </button>

      <dialog
        ref={dialog}
        aria-label="Search the shop"
        onClose={() => {
          setOpen(false);
          setQ("");
          setActive(0);
        }}
        onClick={(e) => {
          if (e.target === dialog.current) close();
        }}
        className="palette-in m-0 mx-auto mt-[8vh] w-[calc(100%-2rem)] max-w-2xl overflow-hidden rounded-lg border border-line bg-surface-1 p-0 text-fg shadow-overlay backdrop:bg-canvas/75"
      >
        {open ? (
          <div onKeyDown={onKey}>
            <div className="flex items-center gap-3 border-b border-line px-4">
              <Search aria-hidden="true" className="size-5 text-accent-text" />
              <input
                ref={input}
                autoFocus
                role="combobox"
                aria-expanded="true"
                aria-controls={`${uid}-list`}
                aria-activedescendant={cur ? `${uid}-o-${cur.it.id}` : undefined}
                aria-autocomplete="list"
                aria-label="Search shoes, occasions and pages"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="Search boots, runners, loafers"
                autoComplete="off"
                spellCheck={false}
                className="h-14 min-w-0 flex-1 bg-transparent text-body text-fg outline-none placeholder:text-fg-muted"
              />
              <Key>Esc</Key>
            </div>

            <div className="grid sm:grid-cols-[1.2fr_1fr]">
              <ul ref={list} role="listbox" id={`${uid}-list`} aria-label="Results" className="max-h-[22rem] overflow-y-auto p-2">
                {flat.length === 0 ? (
                  <li role="presentation" className="grid h-40 place-items-center px-6 text-center text-small text-fg-muted">
                    Nothing matches “{q}”. Try fewer letters.
                  </li>
                ) : null}
                {groups.map((g) => (
                  <li key={g.name} role="presentation">
                    <p className="px-3 pb-1 pt-2 font-mono text-caption uppercase text-fg-muted">{g.name}</p>
                    <ul role="group" aria-label={g.name}>
                      {g.rows.map(({ it, idx }) => {
                        counter++;
                        const i = counter;
                        const on = i === active;
                        return (
                          <li
                            key={it.id}
                            id={`${uid}-o-${it.id}`}
                            data-i={i}
                            role="option"
                            aria-selected={on}
                            onMouseMove={() => setActive(i)}
                            onClick={() => go(it)}
                            className={`flex min-h-11 cursor-pointer items-center gap-3 rounded-md border-l-2 px-3 py-1.5 text-small ${on ? "border-accent bg-accent-soft" : "border-transparent"}`}
                          >
                            <span className="min-w-0 flex-1">
                              <span className="block truncate text-fg">
                                <Lit text={it.title} idx={idx} />
                              </span>
                              {it.subtitle ? <span className="block truncate text-small text-fg-muted">{it.subtitle}</span> : null}
                            </span>
                            {it.product ? <Price minor={it.product.minPriceMinor} currency={it.product.currency} className="shrink-0 text-small" /> : null}
                          </li>
                        );
                      })}
                    </ul>
                  </li>
                ))}
              </ul>

              <div aria-hidden="true" className="hidden border-l border-line p-4 sm:block">
                {cur?.it.product ? (
                  <div>
                    <div className="overflow-hidden rounded-md">
                      <ProductImage product={cur.it.product} label={false} decorative className="aspect-[4/3]!" />
                    </div>
                    <p className="mt-3 font-mono text-caption uppercase text-fg-muted">{cur.it.product.brand}</p>
                    <p className="mt-0.5 text-body font-semibold text-fg-strong">{cur.it.product.name}</p>
                    <div className="mt-1 flex items-baseline justify-between">
                      <span className="text-small text-fg-muted">{cur.it.product.categoryName}</span>
                      <Price minor={cur.it.product.minPriceMinor} currency={cur.it.product.currency} className="text-small" />
                    </div>
                  </div>
                ) : cur ? (
                  <div>
                    <p className="font-mono text-caption uppercase text-fg-muted">{cur.it.group}</p>
                    <p className="mt-1 text-h2 font-bold text-fg-strong">{cur.it.title}</p>
                    {cur.it.subtitle ? <p className="mt-1 text-small text-fg-muted">{cur.it.subtitle}</p> : null}
                  </div>
                ) : null}
              </div>
            </div>

            <div className="flex items-center justify-between border-t border-line px-4 py-2.5 text-small text-fg-muted">
              <span aria-live="polite">
                {flat.length} {flat.length === 1 ? "result" : "results"}
              </span>
              <span aria-hidden="true" className="flex items-center gap-3">
                <span className="flex items-center gap-1">
                  <Key>
                    <ArrowUp className="size-3" />
                  </Key>
                  <Key>
                    <ArrowDown className="size-3" />
                  </Key>
                  move
                </span>
                <span className="flex items-center gap-1">
                  <Key>
                    <CornerDownLeft className="size-3" />
                  </Key>
                  open
                </span>
              </span>
            </div>
          </div>
        ) : null}
      </dialog>
    </>
  );
}
