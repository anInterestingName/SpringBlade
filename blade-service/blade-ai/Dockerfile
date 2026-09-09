FROM bladex/alpine-java:openjdk21_cn_slim

MAINTAINER bladejava@qq.com

RUN mkdir -p /blade/ai

WORKDIR /blade/ai

EXPOSE 8107

ADD ./target/blade-ai.jar ./app.jar

ENTRYPOINT ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

CMD ["--spring.profiles.active=test"]
