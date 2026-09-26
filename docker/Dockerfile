# syntax=docker/dockerfile:1

# Stage 1: Build the OpenSearch Analysis Thaibreak Plugin
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew bundlePlugin -Dopensearch.version=2.18.0

# Stage 2: OpenSearch 2.18.0 with pre-installed Thaibreak plugin
FROM opensearchproject/opensearch:2.18.0
USER root
COPY --from=builder /workspace/build/distributions/analysis-thaibreak-*.zip /tmp/plugin.zip
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch file:///tmp/plugin.zip && rm /tmp/plugin.zip
USER opensearch

EXPOSE 9200 9600
