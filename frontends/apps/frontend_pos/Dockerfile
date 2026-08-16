# syntax=docker/dockerfile:1
# Build from monorepo root: docker build -f apps/frontend_pos/Dockerfile .

FROM node:24-alpine AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml .browserslistrc ./
COPY packages packages
COPY apps/frontend_pos apps/frontend_pos

RUN pnpm install --frozen-lockfile
WORKDIR /app/apps/frontend_pos
ARG VITE_API_URL=
ENV VITE_API_URL=$VITE_API_URL
RUN pnpm build

FROM nginx:1.29-alpine
COPY apps/frontend_pos/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/apps/frontend_pos/dist /usr/share/nginx/html
EXPOSE 80
