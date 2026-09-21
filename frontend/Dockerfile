# Build em dois estágios: Node compila o front; o nginx (imagem mínima) só serve os arquivos estáticos.

# ---------- Estágio 1: build ----------
FROM node:22-alpine AS build
WORKDIR /app

# package.json/lock primeiro: enquanto as dependências não mudarem, esta camada fica em cache.
# "npm ci" instala EXATAMENTE o que está no package-lock.json (build reproduzível).
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# "npm run build" roda tsc (checagem de tipos) antes do Vite: erro de tipo quebra o build.
RUN npm run build

# ---------- Estágio 2: runtime ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
