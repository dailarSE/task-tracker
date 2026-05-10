FROM nginx:1.28.0-alpine

ARG VERSION=unknown

RUN rm /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/
COPY . /usr/share/nginx/html

EXPOSE 80

RUN find /usr/share/nginx/html -type f -name "*.html" -exec sed -i "s/v=localVersion/v=$VERSION/g" {} +

RUN find /usr/share/nginx/html -type f -name "*.html" -exec sed -i "s/Version: localVersion/Version: $VERSION/g" {} +

CMD ["nginx", "-g", "daemon off;"]