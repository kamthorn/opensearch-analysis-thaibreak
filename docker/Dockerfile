# syntax=docker/dockerfile:1

ARG OPENSEARCH_VERSION=2.18.0

# Stage 1: Build the OpenSearch Analysis Thaibreak Plugin
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew packageAllDistributions

# Stage 2: OpenSearch with pre-installed Thaibreak plugin
FROM opensearchproject/opensearch:${OPENSEARCH_VERSION}
ARG OPENSEARCH_VERSION=2.18.0
USER root
COPY --from=builder /workspace/build/distributions/analysis-thaibreak-${OPENSEARCH_VERSION}.0.zip /tmp/plugin.zip
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch file:///tmp/plugin.zip && rm -f /tmp/plugin.zip
USER opensearch

EXPOSE 9200 9600
