# Frontend (React / Vite) image for Amazon ECS: static files served by nginx
#
# Multi-stage build: stage 1 builds the static files (HTML / JS / CSS) with Node.js, stage 2 copies
# only those files into an nginx image. Node.js, node_modules and the source code are not in the
# image that runs in production.
#
# The load balancer routes "/api" to the backend on the same domain (see src/cmn/Constant.ts),
# so nginx only serves the static files.
#
# Build locally:  docker build -t cross-border-payment-frontend ./frontend

# ===========================
# 1. Build stage (Node.js)
# ===========================
# Small Node.js 18 image (Alpine Linux); "AS build" names the stage so stage 2 can copy from it
FROM node:18.20-alpine AS build

# All following commands run in this directory inside the build container
WORKDIR /workspace

# Copy only package.json and the lockfile first.
# Docker caches each layer: as long as these two files do not change, "npm ci" below is reused
# and only the source code layers are rebuilt.
COPY package.json package-lock.json ./

# Install exactly the versions in package-lock.json (fails if it does not match package.json)
RUN npm ci

# Copy the application source code (node_modules, build output and .env files are excluded by .dockerignore)
COPY . .

# Type-check and build the static files into /workspace/build (vite.config.ts: outDir "build")
RUN npm run build

# ===========================
# 2. Runtime stage (nginx)
# ===========================
# nginx image that runs as an unprivileged user (not root) and therefore listens on 8080 instead of 80
FROM nginxinc/nginx-unprivileged:1.27-alpine

# Replace the default site with our configuration (SPA routing, security headers, caching; see nginx.conf)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy only the built static files from the build stage into nginx's document root
COPY --from=build /workspace/build /usr/share/nginx/html

# Documents the port nginx listens on (the ECS task definition / load balancer maps it).
# nginx is started by the base image's default command, so no ENTRYPOINT is needed here.
EXPOSE 8080
