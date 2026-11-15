"use client";

import { useRouter } from "next/navigation";

const OPTIONS = [
  { value: "relevance", label: "Best match" },
  { value: "newest", label: "Newest" },
  { value: "price-asc", label: "Price: low to high" },
  { value: "price-desc", label: "Price: high to low" },
];

export function SortSelect({ base, value, searching }: { base: string; value: string; searching: boolean }) {
  const router = useRouter();
  return (
    <div className="flex items-center gap-2">
      <label htmlFor="sort" className="text-small text-fg-muted">
        Sort by
      </label>
      <select
        id="sort"
        value={value}
        onChange={(e) => router.push(`${base}${base.includes("?") ? "&" : "?"}sort=${e.target.value}`)}
        className="min-h-11 rounded-md border border-line-strong bg-surface-2 px-3 text-small text-fg"
      >
        {OPTIONS.filter((o) => o.value !== "relevance" || searching).map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}
