# syntax=docker/dockerfile:1

# ---------- Stage 1: build the frontend ----------
FROM node:22-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# ---------- Stage 2: build the backend, embedding the built frontend ----------
FROM eclipse-temurin:17-jdk-jammy AS backend-build
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw ./
COPY backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src ./src
# The built SPA lands on the backend's classpath as classpath:/static/,
# which SpaWebConfig serves at the same origin as /api/**.
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static
RUN ./mvnw -q -B -DskipTests package

# ---------- Stage 3: slim runtime ----------
FROM eclipse-temurin:17-jre-alpine AS runtime
RUN addgroup -S bankingapp && adduser -S bankingapp -G bankingapp
WORKDIR /app
COPY --from=backend-build /app/backend/target/*.jar app.jar
USER bankingapp

ENV PORT=8080
EXPOSE 8080

# Conservative default heap for free-tier hosts; override via JAVA_OPTS if needed.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
