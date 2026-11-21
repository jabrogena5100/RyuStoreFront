# syntax=docker/dockerfile:1
# The storefront frontend as one container: Next.js standalone server on port 3000, non-root.
# Build on the dev VM from the repository root (see scripts/ecr-push.sh):
#   docker build --network host -f web/Dockerfile --build-arg API_ORIGIN=https://api.ryustorefront.com web
# API_ORIGIN is fixed at build time for the /api rewrite and read again at run time by server components and /session,
# so the task definition sets the same value.

FROM node:26.13.1-bookworm-slim AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1 NEXT_OUTPUT=standalone
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
ARG API_ORIGIN
RUN test -n "$API_ORIGIN" || { echo "--build-arg API_ORIGIN is required"; exit 1; }
ENV API_ORIGIN=$API_ORIGIN
# next/font downloads Geist from Google Fonts here and serves the subsets from this origin afterwards.
RUN npm run build

FROM node:26.13.1-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
USER node
EXPOSE 3000
CMD ["node", "server.js"]
