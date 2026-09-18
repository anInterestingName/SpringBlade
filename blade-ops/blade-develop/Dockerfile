FROM bladex/alpine-java:openjdk21_cn_slim

LABEL org.opencontainers.image.authors="bladejava@qq.com"

RUN mkdir -p /blade/develop && chown -R 10001:10001 /blade/develop

WORKDIR /blade/develop

EXPOSE 7007

COPY --chown=10001:10001 ./target/blade-develop.jar ./app.jar

USER 10001:10001

ENTRYPOINT ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/blade/develop/app.jar"]
