FROM node:24-alpine AS build

WORKDIR /app

ARG VITE_GOOGLE_CLIENT_ID
# Empty by default: the app calls the backend on its own origin through nginx (T07)
ARG VITE_API_URL=""

ENV VITE_GOOGLE_CLIENT_ID=${VITE_GOOGLE_CLIENT_ID}
ENV VITE_API_URL=${VITE_API_URL}

COPY package*.json .

RUN npm ci

COPY . .

RUN npm run build

FROM nginx:stable-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY proxy_headers.inc /etc/nginx/conf.d/proxy_headers.inc

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
