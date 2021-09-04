FROM adoptopenjdk/openjdk11:centos as builder
ARG APP_VERSION
ENV CURRENT_VERSION APP_VERSION
COPY ssh-key/docker-key /root/.ssh/docker-key
COPY ssh-key/docker-key.pub /root/.ssh/docker-key.pub
RUN set -eux; \
    printf "Host github.com \n  Hostname github.com \n  IdentityFile ~/.ssh/docker-key \n  IdentitiesOnly yes" > /root/.ssh/config; \
    yum -y install git; \
    ssh-keyscan github.com >> /root/.ssh/known_hosts; \
    mkdir /app; \
    git clone git@github.com:psstepniewski/linkshortener.git app; \
    yum -y install curl; \
    curl -L https://www.scala-sbt.org/sbt-rpm.repo > sbt-rpm.repo; \
    mv sbt-rpm.repo /etc/yum.repos.d/; \
    yum -y install sbt; \
    cd app; \
    git checkout "$APP_VERSION"; \
    sbt dist

FROM alpine:3.14
COPY --from=builder /app/target/universal/linkshortener.zip /srv/linkshortener.zip
WORKDIR /srv
RUN set -eux; \
    apk update && apk add --no-cache tcpdump nano tzdata && cp /usr/share/zoneinfo/Europe/Warsaw /etc/localtime && echo "Europe/Warsaw" > /etc/timezone && apk del tzdata && adduser --no-create-home --disabled-password --gecos "" --uid 2727 wheelfred wheelfred; \
    apk add --no-cache openjdk11-jre; \
    apk add --no-cache bash; \
    unzip linkshortener.zip; \
    mv linkshortener app; \
    rm -rf /srv/linkshortener.zip; \
    chown -R wheelfred:wheelfred /srv/*
VOLUME ["/srv/logs"]
CMD ./app/bin/linkshortener \
  -Dconfig.file=/srv/app/conf/linkshortener.conf \
  -Ddb.default.migration.auto=true \
  -Dhttp.port=13256