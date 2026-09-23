# ---------------------------------------------------------------------------
# 后端镜像：多阶段构建（Maven 构建 jar → layertools 分层 → JRE 运行）
# 基础镜像源前缀可通过构建参数覆盖：本地默认国内源 docker.1ms.run，
# 海外环境（如 GitHub Actions）构建时传 BASE_IMAGE_PREFIX=（空）走官方源
# ---------------------------------------------------------------------------
ARG BASE_IMAGE_PREFIX=docker.1ms.run/

FROM ${BASE_IMAGE_PREFIX}library/maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先拷依赖清单并预拉依赖，形成缓存层：源码变更时无需重新下载依赖
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
# layertools 解出 dependencies / spring-boot-loader / snapshot-dependencies /
# application 四层目录：依赖不变时仅 application 层随业务代码重建
RUN mvn -q -B package -DskipTests \
    && java -Djarmode=layertools -jar target/*.jar extract --destination /extracted

# ---------------------------------------------------------------------------
# 运行层：alpine 变体体积小且自带 busybox wget（供容器健康检查使用）
# ---------------------------------------------------------------------------
FROM ${BASE_IMAGE_PREFIX}library/eclipse-temurin:21-jre-alpine

ENV TZ=Asia/Shanghai

WORKDIR /app
# 本地文件存储落盘 ./files（compose 将 named volume 挂载到此目录）
RUN mkdir -p /app/files

# 按 layertools 依赖优先级分层拷贝：改动业务代码仅重建 application 层
COPY --from=builder /extracted/dependencies/ ./
COPY --from=builder /extracted/spring-boot-loader/ ./
COPY --from=builder /extracted/snapshot-dependencies/ ./
COPY --from=builder /extracted/application/ ./

EXPOSE 8080

# exec form + 容器内存感知：堆上限为限额 75%（余量留给 metaspace/堆外），
# OOM 立即退出交由容器重启策略拉起，不假死
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
