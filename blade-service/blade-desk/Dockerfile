FROM bladex/alpine-java:openjdk21_cn_slim

LABEL org.opencontainers.image.authors="bladejava@qq.com"

RUN mkdir -p /blade/desk && chown -R 10001:10001 /blade/desk

WORKDIR /blade/desk

EXPOSE 8105

COPY --chown=10001:10001 ./target/blade-desk.jar ./app.jar

USER 10001:10001

ENTRYPOINT ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/blade/desk/app.jar"]
