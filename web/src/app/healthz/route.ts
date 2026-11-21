/** Load balancer health check: the Node server is up. Deliberately does not call the API (that has its own check). */
export const dynamic = "force-dynamic";

export function GET() {
  return new Response("ok", { headers: { "content-type": "text/plain", "cache-control": "no-store" } });
}
