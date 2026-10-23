# ============================================================
# Frontend — Angular SPA served by nginx
# ============================================================

# ---------- Stage 1: build ----------
FROM node:22-alpine AS build

WORKDIR /build

# npm ci needs both files; caching this layer avoids reinstalling on code edits.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# The commit the bundle came from, served next to index.html. A stale UI looks
# exactly like a fix that did not work, and this is how to tell the two apart
# without shell access to the host.
ARG GIT_SHA=unknown
ARG BUILD_TIME=unknown
RUN printf '{"sha":"%s","built":"%s"}\n' "${GIT_SHA}" "${BUILD_TIME}" \
 > /build/dist/frontend/browser/version.json

# ---------- Stage 2: serve ----------
FROM nginx:1.27-alpine

ARG GIT_SHA=unknown
LABEL org.opencontainers.image.revision="${GIT_SHA}"

# Angular's application builder emits into dist/<project>/browser
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
