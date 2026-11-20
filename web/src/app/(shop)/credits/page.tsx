import type { Metadata } from "next";
import { Container } from "@/components/ui/Container";
import credits from "@/data/image-credits.json";

export const metadata: Metadata = { title: "Image credits" };

type Credit = {
  key: string;
  product: string;
  productName: string;
  source: "pexels" | "pixabay" | "wikimedia";
  pageUrl: string;
  author: string;
  authorUrl: string | null;
  licenceName: string;
  licenceUrl: string;
  retrieved: string;
};

const SOURCE = { pexels: "Pexels", pixabay: "Pixabay", wikimedia: "Wikimedia Commons" } as const;
const link = "text-accent-text underline underline-offset-4 hover:text-accent-hover";

/**
 * Every product photo, who took it, where it came from and under which licence. Generated from content/catalogue/manifest.json
 * by scripts/images/fetch.py. Product names and brands in this shop are invented; the photographers did not endorse them.
 */
export default function Credits() {
  const rows = credits as Credit[];
  const sources = [...new Set(rows.map((r) => r.source))];
  return (
    <Container className="py-10 sm:py-14">
      <p className="font-mono text-caption uppercase text-fg-muted">About this demo</p>
      <h1 className="mt-3 text-display font-bold text-fg-strong">Image credits</h1>
      <div className="mt-4 max-w-2xl space-y-3 text-body text-fg-muted">
        <p>
          Every product photo is used under the licence listed next to it: the{" "}
          <a className={link} href="https://www.pexels.com/license/">Pexels License</a>, the{" "}
          <a className={link} href="https://pixabay.com/service/license-summary/">Pixabay Content License</a>,{" "}
          <a className={link} href="https://creativecommons.org/publicdomain/zero/1.0/">CC0</a> or the public domain. Thank you to the photographers.
        </p>
        <p>
          This is a demo store: product names, brands, prices and descriptions are invented and are not endorsed by the photographers or any
          maker shown. Payments run in Stripe test mode.
        </p>
        <p className="font-mono text-small">
          Photos provided by <a className={link} href="https://www.pexels.com">Pexels</a> and <a className={link} href="https://pixabay.com">Pixabay</a>
          {sources.includes("wikimedia") ? <>, and <a className={link} href="https://commons.wikimedia.org">Wikimedia Commons</a></> : null}.
        </p>
      </div>

      {rows.length === 0 ? (
        <p className="mt-10 rounded-md border border-line bg-surface-1 p-5 text-body text-fg-muted">No photographs are in the store yet; products show placeholders.</p>
      ) : (
        <div className="mt-10 overflow-x-auto">
          <table className="w-full min-w-[40rem] border-collapse text-left text-small">
            <caption className="sr-only">{rows.length} photos with their author, source and licence</caption>
            <thead className="font-mono text-caption uppercase text-fg-muted">
              <tr className="border-b border-line">
                <th scope="col" className="py-3 pr-4 font-normal">Product</th>
                <th scope="col" className="py-3 pr-4 font-normal">Photo by</th>
                <th scope="col" className="py-3 pr-4 font-normal">Source</th>
                <th scope="col" className="py-3 pr-4 font-normal">Licence</th>
                <th scope="col" className="py-3 font-normal">Retrieved</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.key} className="border-b border-line align-top">
                  <th scope="row" className="py-3 pr-4 font-normal text-fg">
                    <a className="hover:underline" href={`/products/${r.product}`}>{r.productName}</a>
                  </th>
                  <td className="py-3 pr-4 text-fg">{r.authorUrl ? <a className={link} href={r.authorUrl}>{r.author}</a> : r.author}</td>
                  <td className="py-3 pr-4"><a className={link} href={r.pageUrl}>{SOURCE[r.source]}</a></td>
                  <td className="py-3 pr-4"><a className={link} href={r.licenceUrl}>{r.licenceName}</a></td>
                  <td className="py-3 font-mono text-fg-muted">{r.retrieved}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Container>
  );
}
