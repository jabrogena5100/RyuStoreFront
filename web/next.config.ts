import type { NextConfig } from "next";

/**
 * The browser only ever talks to this app. `/api/*` is proxied to the Spring API, so the session and cart cookies stay
 * first-party (no CORS, SameSite=Lax works) and the CSRF flow in docs/api/auth.md needs nothing extra.
 * API_ORIGIN is read when `next build` runs (rewrites are fixed at build time); server components read it at run time.
 */
const apiOrigin = process.env.API_ORIGIN ?? "http://localhost:8080";

const config: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiOrigin}/api/:path*` }];
  },
};

export default config;
