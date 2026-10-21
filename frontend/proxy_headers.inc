# Shared by every location that proxies to the backend (included from nginx.conf)
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
# nginx is the only proxy: overwrite (never append to) what the client sent, otherwise a fake
# X-Forwarded-For would become the "client IP" that Spring uses for rate limiting
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host $host;
proxy_set_header X-Request-Id $request_id;
