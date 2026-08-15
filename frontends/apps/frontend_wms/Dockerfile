# syntax=docker/dockerfile:1

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL=
ARG VITE_ENABLE_CHATBOT=true
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_ENABLE_CHATBOT=$VITE_ENABLE_CHATBOT
# Align resolve script + Vite define with the build arg (backend/frontend toggle together via deploy.bat).
RUN if [ "$VITE_ENABLE_CHATBOT" = "false" ]; then \
      node scripts/resolve-chatbot.mjs --disable; \
    else \
      node scripts/resolve-chatbot.mjs --enable; \
    fi
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
