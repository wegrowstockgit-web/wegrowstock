# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY invsys-core/pom.xml invsys-core/pom.xml
COPY invsys-chatbot/pom.xml invsys-chatbot/pom.xml
COPY invsys-app/pom.xml invsys-app/pom.xml
RUN mvn -q dependency:go-offline -pl invsys-app -am || true
COPY invsys-core invsys-core
COPY invsys-chatbot invsys-chatbot
COPY invsys-app invsys-app
ARG WITH_CHATBOT=true
# maven.test.skip=true: omit test compile (support ITs need chatbot on the classpath).
# -P=!with-chatbot: deactivate the default with-chatbot profile (omit Spring AI module).
RUN if [ "$WITH_CHATBOT" = "false" ]; then \
      mvn -q -Dmaven.test.skip=true package -pl invsys-app -am "-P=!with-chatbot"; \
    else \
      mvn -q -Dmaven.test.skip=true package -pl invsys-app -am; \
    fi

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S invsys && adduser -S invsys -G invsys
WORKDIR /app
COPY --from=build /app/invsys-app/target/*.jar app.jar
USER invsys
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
