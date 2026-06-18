# xwBackend 部署指南

## 176 部署现状

| 服务 | 形态 | 端口 |
|------|------|------|
| **xwBackend** | 宿主机裸 jar（`/home/yinbanghu/176-deploy/backend/`） | 8081 |
| **xianwei-nginx** | docker 容器（统一对外入口） | 8529 → 80 |
| xianwei-algorithm | docker 容器 | 9203 |
| xianwei-embedding | docker 容器 | 5002 |
| xianwei-mysql | docker 容器 | 9204 |
| xianwei-minio | docker 容器 | 9205 / 9206 |

**对外入口统一走 `http://36.141.21.176:8529`**，nginx 反代到后端 8081。

后端可选择**裸 jar**（当前生产形态，简单直接）或 **Docker 容器**部署，两种方式互斥，二选一。

---

## 方式 A：裸 jar 部署（当前生产形态）

### A.1 本地打包

```bash
cd xwSystem/xwBackend
./mvnw -DskipTests clean package
# 产物：target/uygur-project-0.0.1-SNAPSHOT.jar
```

### A.2 上传 jar 到 176

```bash
scp -P 1111 target/uygur-project-0.0.1-SNAPSHOT.jar \
  yinbanghu@36.141.21.176:/home/yinbanghu/176-deploy/backend/
```

### A.3 重启后端

```bash
ssh -p 1111 yinbanghu@36.141.21.176

# kill 旧进程
kill $(pgrep -f 'uygur-project')

# 启动（必须 cd 到 backend 目录，否则找不到 ./config/application-local.yml）
cd /home/yinbanghu/176-deploy/backend
nohup /home/yinbanghu/liuyukun/jdk8u492-b09/bin/java \
  -Xms512m -Xmx2g -XX:+UseG1GC \
  -jar uygur-project-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local \
  > nohup.out 2>&1 < /dev/null &
disown
```

### A.4 验证

```bash
curl -s http://localhost:8081/actuator/health    # 直连
curl -s http://localhost:8529/api/uygur/config   # 经 nginx
```

### A.5 配置文件

| 文件 | 用途 |
|------|------|
| `/home/yinbanghu/176-deploy/backend/config/application.yml` | 主配置（端口 8081、management、springdoc）|
| `/home/yinbanghu/176-deploy/backend/config/application-local.yml` | local profile 环境变量（DB / MinIO / ES 等）|

---

## 方式 B：Docker 容器部署

`/home/yinbanghu/176-deploy/backend/Dockerfile` 已经写好。流程：

### B.1 本地打包 + 上传 jar

同方式 A 的 A.1 / A.2。

### B.2 176 上构建镜像 + 启动容器

```bash
ssh -p 1111 yinbanghu@36.141.21.176
cd /home/yinbanghu/176-deploy/backend

# 停掉裸 jar（如果在跑）
kill $(pgrep -f 'uygur-project') 2>/dev/null

# 构建镜像
docker build -t xianwei-backend:latest .

# 删旧容器（如有）
docker rm -f xianwei-backend 2>/dev/null

# 启动新容器
docker run -d --name xianwei-backend \
  --restart unless-stopped \
  --network 176-deploy_xianwei-net \
  -p 8081:8081 \
  -v /home/yinbanghu/176-deploy/backend/config:/app/config:ro \
  -v /home/yinbanghu/176-deploy/backend/logs:/app/logs \
  -e SPRING_PROFILES_ACTIVE=local \
  xianwei-backend:latest
```

**两点关键**：
- `--network 176-deploy_xianwei-net`：和 nginx 同网络，nginx.conf 里 proxy_pass 可改成 `http://xianwei-backend:8081`（更规范，见 B.4）
- 配置文件 `config/` 整目录挂载，启动时 `cwd=/app`，能正确读到 `./config/application-local.yml`

### B.3 验证

```bash
docker ps --filter name=xianwei-backend
docker logs --tail 30 xianwei-backend | grep Started
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8529/api/uygur/config
```

### B.4 用容器模式时优化 nginx 配置（可选）

容器模式下 nginx 和 backend 在同一网络，直接用 docker DNS 解析：

```nginx
location ~ ^/(api|extraction|test|actuator|druid|swagger-ui|v3)/ {
    proxy_pass http://xianwei-backend:8081;   # 容器名解析
    ...
}
```

改完后 reload：
```bash
docker exec xianwei-nginx nginx -s reload
```

---

## 两种方式对比

| 维度 | 裸 jar | Docker 容器 |
|------|--------|------------|
| 部署速度 | 快（仅传 jar）| 慢（要 build 镜像） |
| 资源隔离 | 无 | 有 |
| 进程管理 | nohup / kill | docker restart / logs |
| nginx 上游 | `192.168.32.1:8081`（宿主机网关） | `xianwei-backend:8081`（容器 DNS） |
| 当前生产 | ✅ 在用 | ⚪ 备选 |
| 推荐场景 | 频繁调试、JVM 参数微调 | 多环境一致、CI/CD |

**重要**：两种方式互斥，**同一时间只能跑一份后端占 8081**。切换前务必停掉旧的（kill 进程 / docker stop）。

---

## nginx 配置

**文件**：`/home/yinbanghu/176-deploy/frontend/nginx.conf`（权限 `yinbanghu:yinbanghu` 644，可直接 vi 编辑）

**反代规则**：
- `/api/*`, `/actuator/*`, `/druid/*`, `/swagger-ui/*`, `/v3/*`, `/extraction/*`, `/test/*` → `http://192.168.32.1:8081`（裸 jar 模式）或 `http://xianwei-backend:8081`（容器模式）
- `/minio/*` → `http://192.168.32.1:9205/`
- `/assets/*` → 7 天缓存
- `/` → SPA fallback（前端 dist）

> 裸 jar 模式下，nginx 容器内通过 docker 网络网关 `192.168.32.1` 访问宿主机后端（不能用 `host.docker.internal`，未配 extra_hosts）。

**容器启动命令**（如需重建 nginx）：

```bash
docker run -d --name xianwei-nginx \
  --restart unless-stopped \
  -p 8529:80 \
  --network 176-deploy_xianwei-net \
  -v /home/yinbanghu/176-deploy/frontend/dist:/usr/share/nginx/html:ro \
  -v /home/yinbanghu/176-deploy/frontend/nginx.conf:/etc/nginx/conf.d/default.conf:ro \
  nginx:1.25-alpine
```

**改配置后 reload（不停服）**：
```bash
docker exec xianwei-nginx nginx -t          # 语法检查
docker exec xianwei-nginx nginx -s reload   # 应用新配置
```

---

## Apifox 接入

**数据源 URL（自动同步接口）**：
```
http://36.141.21.176:8529/v3/api-docs
```

**环境前缀**：
```
http://36.141.21.176:8529
```

接口只写相对路径 `/api/xxx`，74 个接口按 11 个中文分组（基于 springdoc + `@Tag`）。
