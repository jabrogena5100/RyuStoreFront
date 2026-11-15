"use client";

import { useMemo, useState } from "react";
import { ButtonLink } from "@/components/ui/Button";
import { Chip } from "@/components/ui/Chip";
import { ALL_SIZES, OCCASIONS, filterProducts } from "@/lib/catalog";

const WHO = [
  { value: "women", label: "Women" },
  { value: "men", label: "Men" },
  { value: "", label: "Anyone" },
];

/** The quick path: who, what for, size, then straight to the matching pairs with a live count. */
export function QuickFinder() {
  const [gender, setGender] = useState("");
  const [occasion, setOccasion] = useState("");
  const [size, setSize] = useState("");

  const count = useMemo(() => filterProducts({ gender, occasion, size, stock: "1" }).length, [gender, occasion, size]);
  const params = new URLSearchParams();
  if (gender) params.set("gender", gender);
  if (occasion) params.set("occasion", occasion);
  if (size) params.set("size", size);
  params.set("stock", "1");

  return (
    <section aria-labelledby="finder" className="rounded-lg border border-line bg-surface-1 p-5 sm:p-6">
      <h2 id="finder" className="text-h2 font-semibold text-fg-strong">
        Find your pair
      </h2>
      <div className="mt-5 flex flex-col gap-5">
        <div>
          <p className="mb-2 text-small font-medium text-fg-muted">Who is it for</p>
          <div className="flex flex-wrap gap-2">
            {WHO.map((w) => (
              <Chip key={w.label} selected={gender === w.value} onClick={() => setGender(w.value)}>
                {w.label}
              </Chip>
            ))}
          </div>
        </div>
        <div>
          <p className="mb-2 text-small font-medium text-fg-muted">What for</p>
          <div className="flex flex-wrap gap-2">
            <Chip selected={occasion === ""} onClick={() => setOccasion("")}>
              Anything
            </Chip>
            {OCCASIONS.map((o) => (
              <Chip key={o.slug} selected={occasion === o.slug} onClick={() => setOccasion(o.slug)}>
                {o.label}
              </Chip>
            ))}
          </div>
        </div>
        <div className="flex flex-col gap-2 sm:max-w-48">
          <label htmlFor="finder-size" className="text-small font-medium text-fg-muted">
            Your size (US)
          </label>
          <select
            id="finder-size"
            value={size}
            onChange={(e) => setSize(e.target.value)}
            className="min-h-11 rounded-md border border-line-strong bg-surface-2 px-3 text-small text-fg"
          >
            <option value="">Any size</option>
            {ALL_SIZES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <ButtonLink href={`/products?${params.toString()}`} size="lg" className="w-full sm:w-auto sm:self-start" aria-live="polite">
          {count === 0 ? "No pairs in stock, see all" : `Show ${count} ${count === 1 ? "pair" : "pairs"} in stock`}
        </ButtonLink>
      </div>
    </section>
  );
}
