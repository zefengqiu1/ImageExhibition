FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
#s make sure have /app backslash at front, otherwise copy can fail
COPY target/imageExhibition-0.0.1-SNAPSHOT.jar /app/imageExhibition-0.0.1-SNAPSHOT.jar

EXPOSE 8081
# 启动 Spring Boot 应用
CMD ["java", "-jar", "/app/imageExhibition-0.0.1-SNAPSHOT.jar"]
