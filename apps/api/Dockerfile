# BLOOD API + website. Build from the repository root:
#   docker build -f apps/api/Dockerfile -t bloom-api .
FROM node:22-alpine AS build
WORKDIR /repo
COPY package.json ./
COPY packages/shared/package.json packages/shared/
COPY apps/api/package.json apps/api/
RUN npm install --no-audit --no-fund -w @bloom/api -w @bloom/shared --include-workspace-root
COPY packages/shared packages/shared
COPY apps/api apps/api
RUN npm run build -w @bloom/api

FROM node:22-alpine
ENV NODE_ENV=production BLOOM_ENV=production PORT=8787
WORKDIR /app
# The bundle inlines @bloom/shared; the only runtime dependency is the PostgreSQL driver.
RUN mkdir -p apps/api && cd apps/api \
  && echo '{"name":"bloom-api-runtime","private":true}' > package.json \
  && npm install --omit=dev --no-audit --no-fund --ignore-scripts pg@^8.13.0 && npm cache clean --force
COPY --from=build /repo/apps/api/dist ./apps/api/dist
COPY apps/api/migrations ./apps/api/migrations
COPY apps/api/content ./apps/api/content
COPY apps/website/public ./apps/website/public
USER node
EXPOSE 8787
HEALTHCHECK --interval=30s --timeout=5s CMD wget -qO- http://127.0.0.1:8787/healthz || exit 1
CMD ["node", "apps/api/dist/server.mjs"]
