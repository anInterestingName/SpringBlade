FROM bladex/alpine-java:openjdk21_cn_slim

LABEL org.opencontainers.image.authors="bladejava@qq.com"

RUN mkdir -p /blade/auth && chown -R 10001:10001 /blade/auth

WORKDIR /blade/auth

EXPOSE 8100

COPY --chown=10001:10001 ./target/blade-auth.jar ./app.jar

USER 10001:10001

ENTRYPOINT ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/blade/auth/app.jar"]
