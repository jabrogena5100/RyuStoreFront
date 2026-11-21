/**
 * Who is signed in, answered with 200 for guests too: `{ customer }` or `{ customer: null }`.
 *
 * The browser used to call `/api/auth/me` directly, and its 401 for a guest is logged by every browser as a console
 * error (Lighthouse best practices, docs/metrics.md F3). This handler asks the API on the server side, forwarding the
 * session cookie, and turns the 401 into a normal answer. The API itself is unchanged; `/api/auth/me` still returns 401.
 */
import type { NextRequest } from "next/server";

const ORIGIN = process.env.API_ORIGIN ?? "http://localhost:8080";

export const dynamic = "force-dynamic";

export async function GET(req: NextRequest) {
  const cookie = req.headers.get("cookie");
  let res: Response;
  try {
    res = await fetch(`${ORIGIN}/api/auth/me`, { headers: cookie ? { cookie } : {}, cache: "no-store" });
  } catch {
    return Response.json({ code: "unreachable", message: "The API could not be reached." }, { status: 502 });
  }
  const headers = new Headers({ "cache-control": "no-store" });
  for (const c of res.headers.getSetCookie()) headers.append("set-cookie", c);
  if (res.status === 401) return Response.json({ customer: null }, { headers });
  if (!res.ok) return Response.json({ code: `http_${res.status}`, message: "Could not check the session." }, { status: 502, headers });
  return Response.json({ customer: await res.json() }, { headers });
}
