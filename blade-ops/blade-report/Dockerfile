FROM bladex/alpine-java:openjdk21_cn_slim

LABEL org.opencontainers.image.authors="bladejava@qq.com"

RUN mkdir -p /blade/report && chown -R 10001:10001 /blade/report

WORKDIR /blade/report

EXPOSE 8108

COPY --chown=10001:10001 ./target/blade-report.jar ./app.jar

USER 10001:10001

ENTRYPOINT ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/blade/report/app.jar"]
