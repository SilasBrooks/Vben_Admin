# ---------------------------------------------------------------------------
# 后端镜像：多阶段构建（Maven 构建 jar → JRE 运行）
# 基础镜像走国内镜像源前缀 docker.1ms.run，海外环境可整体去掉该前缀
# ---------------------------------------------------------------------------
FROM docker.1ms.run/library/maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先拷依赖清单并预拉依赖，形成缓存层：源码变更时无需重新下载依赖
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------------------------------------------------------------------------
# 运行层：alpine 变体体积小且自带 busybox wget（供容器健康检查使用）
# ---------------------------------------------------------------------------
FROM docker.1ms.run/library/eclipse-temurin:21-jre-alpine

ENV TZ=Asia/Shanghai

WORKDIR /app
# 本地文件存储落盘 ./files（compose 将 named volume 挂载到此目录）
RUN mkdir -p /app/files

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
