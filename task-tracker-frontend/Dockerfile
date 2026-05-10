FROM nginx:1.28.0-alpine

ARG VERSION=unknown

RUN rm -f /etc/nginx/conf.d/default.conf

COPY default.conf.template /etc/nginx/templates/default.conf.template
COPY . /usr/share/nginx/html

EXPOSE 80

RUN find /usr/share/nginx/html -type f -name "*.html" -exec sed -i "s/v=localVersion/v=$VERSION/g" {} +

RUN find /usr/share/nginx/html -type f -name "*.html" -exec sed -i "s/Version: localVersion/Version: $VERSION/g" {} +

RUN rm -f /usr/share/nginx/html/Dockerfile \
          /usr/share/nginx/html/default.conf.template \
          /usr/share/nginx/html/nginx.conf

ENV BACKEND_HOST=task-tracker-backend

CMD ["nginx", "-g", "daemon off;"]