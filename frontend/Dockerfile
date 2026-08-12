FROM node:26-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

# nginx instead of the "serve" npm package: it's the standard way to serve a built SPA,
# it's lighter weight, and — unlike serve — this config actually proxies /api requests to
# the backend, which the previous setup was silently missing (Vite's dev-time proxy in
# vite.config.ts only applies to `npm run dev`; the production build had no equivalent,
# so every API call from the browser would have 404'd once the container did start).
#
# nginx-unprivileged instead of the plain nginx image — the stock image runs its master
# process as root (needed to bind port 80, a privileged port); this variant is
# purpose-built to run entirely as a non-root user on port 8080, per the security plan's
# "non-root containers" commitment, without needing to hand-manage nginx's PID/cache
# directory permissions ourselves.
FROM nginxinc/nginx-unprivileged:alpine
# This base image already switches to a non-root "nginx" user internally, so apk (which
# needs root) has to explicitly switch back before upgrading and back again afterward —
# same reasoning as backend/Dockerfile: refresh Alpine's packages at build time instead
# of trusting a possibly-stale base image tag, so OS-level CVEs get picked up on every
# build without needing to remember to bump the base image.
USER root
RUN apk update && apk upgrade --no-cache
USER nginx
COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
