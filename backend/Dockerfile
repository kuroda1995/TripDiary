# TripDiary API のコンテナイメージ（docs/deploy.md）
#   ビルド: docker build --platform linux/amd64 -t tripdiary-api backend
#   本番（ECS Fargate）では SPRING_PROFILES_ACTIVE=prod と、DB・JWT・S3 の環境変数を渡して起動する

# ---- ビルド ----
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
# 依存の取得だけを先に行い、ソースの変更時にキャッシュを効かせる
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon --console=plain > /dev/null
COPY src src
# テストは CI（ci.yml）で実行済みのため、ここでは実行しない
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon --console=plain \
    && cp build/libs/backend-0.0.1-SNAPSHOT.jar /workspace/app.jar

# ---- 実行 ----
FROM eclipse-temurin:21-jre-noble
WORKDIR /app
# root 以外のユーザーで動かす
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/app.jar app.jar
USER app
# コンテナのメモリ上限に合わせてヒープを決める
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
