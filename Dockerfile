FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY . .
RUN ./mvnw -q -DskipTests compile dependency:copy-dependencies -DincludeScope=runtime

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/target/classes /app/classes
COPY --from=build /workspace/target/dependency /app/libs

ENV JAVA_OPTS=""
ENV MAIN_CLASS="com.tms.thesissystem.ThesisSystemApplication"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp /app/classes:/app/libs/* $MAIN_CLASS"]
