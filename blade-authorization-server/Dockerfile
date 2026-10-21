FROM bladex/alpine-java:openjdk21_cn_slim
LABEL org.opencontainers.image.authors="SpringBlade contributors"
RUN mkdir -p /blade/authorization && chown -R 10001:10001 /blade/authorization
WORKDIR /blade/authorization
COPY --chown=10001:10001 ./target/blade-authorization-server.jar ./app.jar
EXPOSE 8100
USER 10001:10001
ENTRYPOINT ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/blade/authorization/app.jar"]
