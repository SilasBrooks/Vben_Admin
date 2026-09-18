# ---------------------------------------------------------------------------
# 前端镜像：多阶段构建（pnpm 构建 web-ele → nginx 托管）
# 注意：构建上下文必须是 front/（monorepo 根），compose 中以
#   context: ./front, dockerfile: apps/web-ele/Dockerfile 指定
# 基础镜像走国内镜像源前缀 docker.1ms.run，海外环境可整体去掉该前缀
# ---------------------------------------------------------------------------
FROM docker.1ms.run/library/node:22-slim AS builder

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
ENV NODE_OPTIONS=--max-old-space-size=8192
ENV TZ=Asia/Shanghai
ENV CI=true

# corepack 按 package.json 的 packageManager 字段激活对应 pnpm 版本
RUN npm i -g corepack && corepack enable

WORKDIR /app

# 构建上下文为 front/，复用 front/.dockerignore（node_modules/dist 等已排除）
COPY . /app

RUN --mount=type=cache,id=pnpm,target=/pnpm/store pnpm install --frozen-lockfile
# turbo 会先构建 web-ele 依赖的 workspace 包，再产出 apps/web-ele/dist
RUN pnpm run build:ele

# ---------------------------------------------------------------------------
FROM docker.1ms.run/library/nginx:stable-alpine AS production

RUN rm -rf /etc/nginx/conf.d/default.conf

COPY apps/web-ele/nginx.docker.conf /etc/nginx/nginx.conf
COPY --from=builder /app/apps/web-ele/dist /usr/share/nginx/html

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
