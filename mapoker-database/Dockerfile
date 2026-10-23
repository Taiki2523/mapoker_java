FROM postgres:16-alpine

# Replace the Go-based privilege-drop helper with Alpine's equivalent.
# PostgreSQL's upstream initialization and upgrade behavior is preserved.
RUN apk upgrade --no-cache \
    && apk add --no-cache su-exec \
    && sed -i 's/exec gosu postgres/exec su-exec postgres/' /usr/local/bin/docker-entrypoint.sh \
    && rm /usr/local/bin/gosu
