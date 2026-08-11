# =============================================================================
# Imagem do frontend FleetOps — build multi-estágio.
# =============================================================================

# --- Estágio 1: build dos estáticos --------------------------------------------
FROM node:24-alpine AS builder
WORKDIR /build

# Dependências primeiro: alterar um componente não invalida o cache do npm.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY tsconfig.json tsconfig.app.json tsconfig.node.json vite.config.ts index.html ./
COPY src ./src
RUN npm run build

# --- Estágio 2: Nginx servindo os estáticos e encaminhando /api ----------------
FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
