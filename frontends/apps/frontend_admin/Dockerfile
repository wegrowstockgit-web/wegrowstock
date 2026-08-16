# syntax=docker/dockerfile:1
# Build from monorepo root: docker build -f apps/frontend_admin/Dockerfile .

FROM node:24-alpine AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml .browserslistrc ./
COPY packages packages
COPY apps/frontend_admin apps/frontend_admin

# Fresh install inside the image (host node_modules are dockerignored).
RUN pnpm install --frozen-lockfile
WORKDIR /app/apps/frontend_admin
ARG VITE_API_URL=
ENV VITE_API_URL=$VITE_API_URL
RUN pnpm build

FROM nginx:1.29-alpine
COPY apps/frontend_admin/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/apps/frontend_admin/dist /usr/share/nginx/html
EXPOSE 80
