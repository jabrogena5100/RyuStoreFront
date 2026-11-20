import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ImagePlus } from "lucide-react";
import { ProductImage } from "@/components/ProductImage";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Field, SelectField, TextAreaField, controlClass } from "@/components/ui/Field";
import { CATEGORY_NAMES, getProduct } from "@/lib/catalog";
import { stockOf } from "@/lib/orders";

export const metadata: Metadata = { title: "Edit product · Admin" };

/** ?state=errors|saved shows validation errors and the saved confirmation. */
export default async function AdminProduct({ params, searchParams }: { params: Promise<{ slug: string }>; searchParams: Promise<{ state?: string }> }) {
  const p = getProduct((await params).slug);
  const { state } = await searchParams;
  if (!p) notFound();
  const errors = state === "errors";
  const variants = stockOf(p);
  const bad = errors ? { name: "Enter a product name.", price: "Enter a price of 0.01 or more.", stock: "Stock cannot be negative." } : null;

  return (
    <div>
      <nav aria-label="Breadcrumb" className="text-small text-fg-subtle">
        <Link href="/admin/products" className="hover:text-fg">Products</Link> <span aria-hidden="true">/</span> <span className="text-fg-muted">{p.name}</span>
      </nav>
      <h1 className="mb-6 mt-3 text-h1 font-semibold text-fg-strong">{p.name}</h1>

      {state === "saved" ? <Alert tone="success" title="Changes saved" className="mb-6">The shop shows the new details straight away.</Alert> : null}
      {errors ? <Alert tone="danger" title="Check the highlighted fields" className="mb-6">Nothing was saved.</Alert> : null}

      <form noValidate className="flex flex-col gap-8">
        <section aria-labelledby="details" className="rounded-lg border border-line bg-surface-1 p-5 sm:p-6">
          <h2 id="details" className="mb-5 text-h2 font-semibold text-fg-strong">Details</h2>
          <div className="grid gap-5 sm:grid-cols-2">
            <Field id="name" name="name" label="Name" defaultValue={errors ? "" : p.name} error={bad?.name} />
            <Field id="brand" name="brand" label="Brand" defaultValue={p.brand} />
            <SelectField id="category" name="category" label="Category" defaultValue={p.categorySlug}>
              {Object.entries(CATEGORY_NAMES).map(([slug, label]) => <option key={slug} value={slug}>{label}</option>)}
            </SelectField>
            <SelectField id="gender" name="gender" label="Sizing" defaultValue={p.gender}>
              <option value="women">Women</option>
              <option value="men">Men</option>
              <option value="unisex">Unisex</option>
            </SelectField>
            <Field id="colour" name="colour" label="Colour" defaultValue={p.colour} />
            <Field id="material" name="material" label="Upper material" defaultValue={p.material} />
            <TextAreaField id="description" name="description" label="Description" rows={3} defaultValue={p.description} className="sm:col-span-2" />
            <TextAreaField id="details" name="details" label="Details" hint="One point per line." rows={4} defaultValue={p.details.join("\n")} className="sm:col-span-2" />
          </div>
        </section>

        <section aria-labelledby="photos" className="rounded-lg border border-line bg-surface-1 p-5 sm:p-6">
          <h2 id="photos" className="mb-1 text-h2 font-semibold text-fg-strong">Photos</h2>
          <p className="mb-5 text-small text-fg-muted">Four images per product at a 4:5 ratio. Until real photos are uploaded, the shop shows these placeholders.</p>
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {([0, 1, 2, 3] as const).map((v) => (
              <li key={v} className="overflow-hidden rounded-md border border-line">
                <ProductImage product={p} view={v} label={false} decorative />
              </li>
            ))}
          </ul>
          <Button variant="secondary" className="mt-5"><ImagePlus aria-hidden="true" className="size-4" /> Upload photos</Button>
          <p className="mt-2 text-caption text-fg-muted">JPEG or PNG, up to 5 MB each. Files go straight to storage; they never pass through the shop server.</p>
        </section>

        <section aria-labelledby="variants" className="rounded-lg border border-line bg-surface-1 p-5 sm:p-6">
          <h2 id="variants" className="mb-1 text-h2 font-semibold text-fg-strong">Sizes, prices and stock</h2>
          <p className="mb-5 text-small text-fg-muted">A size with 0 pairs shows as sold out in the shop.</p>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[34rem] text-left text-small">
              <caption className="sr-only">Variants</caption>
              <thead className="text-caption uppercase text-fg-muted">
                <tr>
                  <th scope="col" className="py-2 pr-3 font-medium">Size (US)</th>
                  <th scope="col" className="py-2 pr-3 font-medium">SKU</th>
                  <th scope="col" className="py-2 pr-3 font-medium">Price (USD)</th>
                  <th scope="col" className="py-2 font-medium">Pairs in stock</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {variants.map((v, i) => {
                  const first = i === 0;
                  return (
                    <tr key={v.sku}>
                      <th scope="row" className="tabular py-2 pr-3 font-medium text-fg">{v.sizeLabel}</th>
                      <td className="py-2 pr-3 text-fg-subtle">{v.sku}</td>
                      <td className="py-2 pr-3">
                        <input aria-label={`Price for size ${v.sizeLabel}`} inputMode="decimal" defaultValue={errors && first ? "0" : (v.priceMinor / 100).toFixed(2)} aria-invalid={errors && first ? true : undefined} aria-describedby={errors && first ? "price-error" : undefined} className={`${controlClass(errors && first ? "x" : undefined)} tabular w-28`} />
                        {errors && first ? <p id="price-error" className="mt-1 text-small text-danger">{bad?.price}</p> : null}
                      </td>
                      <td className="py-2">
                        <input aria-label={`Pairs in stock for size ${v.sizeLabel}`} inputMode="numeric" defaultValue={errors && i === 1 ? "-2" : String(v.stock)} aria-invalid={errors && i === 1 ? true : undefined} aria-describedby={errors && i === 1 ? "stock-error" : undefined} className={`${controlClass(errors && i === 1 ? "x" : undefined)} tabular w-24`} />
                        {errors && i === 1 ? <p id="stock-error" className="mt-1 text-small text-danger">{bad?.stock}</p> : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>

        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" size="lg">Save changes</Button>
          <Link href="/admin/products" className="inline-flex min-h-11 items-center rounded-md px-4 text-small font-medium text-fg-muted hover:bg-surface-2 hover:text-fg">Cancel</Link>
        </div>
      </form>
    </div>
  );
}
