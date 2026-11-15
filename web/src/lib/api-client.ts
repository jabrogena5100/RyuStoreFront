/**
 * The browser's door to the API: same-origin `/api/*` (proxied to Spring by next.config.ts), cookies on every call,
 * the CSRF header on every write (docs/api/auth.md): fetch a token once, reuse it, take the new one that login and
 * register return, and on a 403 `csrf_invalid` fetch a fresh one and retry once.
 */
export class ApiClientError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fields: Record<string, string> = {},
  ) {
    super(message);
  }
}

interface Csrf {
  headerName: string;
  token: string;
}

let csrf: Csrf | null = null;
let pending: Promise<Csrf> | null = null;

export function setCsrf(next: Csrf | null) {
  csrf = next;
}

async function loadCsrf(): Promise<Csrf> {
  pending ??= fetch("/api/auth/csrf", { cache: "no-store" })
    .then((r) => {
      if (!r.ok) throw new ApiClientError(r.status, "csrf_unavailable", "Could not get a security token.");
      return r.json() as Promise<Csrf>;
    })
    .then((c) => (csrf = c))
    .finally(() => {
      pending = null;
    });
  return pending;
}

async function parseError(res: Response): Promise<ApiClientError> {
  let body: { code?: string; message?: string; fields?: Record<string, string> } = {};
  try {
    body = await res.json();
  } catch {
    /* not JSON */
  }
  return new ApiClientError(res.status, body.code ?? `http_${res.status}`, body.message ?? res.statusText, body.fields);
}

export async function api<T = void>(path: string, opts: { method?: "GET" | "POST" | "PUT" | "DELETE"; body?: unknown; headers?: Record<string, string> } = {}): Promise<T> {
  const method = opts.method ?? "GET";
  const write = method !== "GET";
  for (let attempt = 0; ; attempt++) {
    const headers: Record<string, string> = { Accept: "application/json", ...opts.headers };
    if (opts.body !== undefined) headers["Content-Type"] = "application/json";
    if (write) {
      const c = csrf ?? (await loadCsrf());
      headers[c.headerName] = c.token;
    }
    let res: Response;
    try {
      res = await fetch(`/api${path}`, { method, headers, body: opts.body === undefined ? undefined : JSON.stringify(opts.body), cache: "no-store" });
    } catch {
      throw new ApiClientError(0, "network", "Could not reach the server.");
    }
    if (res.ok) return (res.status === 204 ? undefined : await res.json()) as T;
    const err = await parseError(res);
    if (write && err.status === 403 && err.code === "csrf_invalid" && attempt === 0) {
      csrf = null; // the session or token rotated; take a new one and try once more
      continue;
    }
    throw err;
  }
}
