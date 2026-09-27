# make sure jdk can be found from docker hub
# 使用官方的 OpenJDK 作为基础镜像
FROM openjdk:17-jdk-slim
WORKDIR /app
#s make sure have /app backslash at front, otherwise copy can fail
COPY target/imageExhibition-0.0.1-SNAPSHOT.jar /app/imageExhibition-0.0.1-SNAPSHOT.jar

EXPOSE 8081
# 启动 Spring Boot 应用
CMD ["java", "-jar", "/app/imageExhibition-0.0.1-SNAPSHOT.jar"]