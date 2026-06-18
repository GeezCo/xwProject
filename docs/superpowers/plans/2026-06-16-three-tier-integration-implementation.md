# 三端联调环境实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 176 服务器搭建独立 nginx 容器服务前端 dist + 反代 backend，打通三端联调，并提供 Mac 端一键增量发布脚本。

**Architecture:** 新增 `xianwei-nginx` 容器（端口 8527）加入现有 `xianwei-net` 网络；前端构建产物挂载只读；nginx 反代 `/api`、`/extraction` 等路径到 `backend:8081`；通过 rsync + `nginx -s reload` 实现秒级前端更新。

**Tech Stack:** Vue 3 + Vite, nginx:1.25-alpine, Docker Compose, bash, expect/scp/rsync。

---

## 文件结构

新建：

- `~/Downloads/176-deploy/frontend/nginx.conf` — nginx 站点配置（同源反代）
- `~/Downloads/176-deploy/frontend/dist/` — 前端构建产物目录（rsync 同步目标）
- `~/Downloads/176-deploy/containers/nginx-image.tar` — nginx:1.25-alpine 镜像 tar（离线加载用）
- `/Users/processmonitor/Documents/WebStormProjects/xianwei_web/scripts/upload-frontend.sh` — Mac 端构建+上传+reload 脚本

修改：

- `/Users/processmonitor/Documents/WebStormProjects/xianwei_web/frontend/src/api/request.js` — 修复 baseURL 兜底逻辑
- `/Users/processmonitor/Documents/WebStormProjects/xianwei_web/frontend/.env.production` — 确认 `VITE_API_BASE_URL=`
- `~/Downloads/176-deploy/docker-compose.yml` — 加 nginx 服务
- `~/Downloads/176-deploy/manage.sh` — 加 `deploy-frontend` 子命令、扩展 SERVICES_UP / IMAGES / HEALTH_URLS / PORTS

每个文件单一职责，互不耦合。

---

## Task 1：修复 axios `baseURL` 兜底逻辑

**Files:**
- Modify: `/Users/processmonitor/Documents/WebStormProjects/xianwei_web/frontend/src/api/request.js`

**背景**：前端业务代码（category.js 等 7 个 api 文件，共约 35 处调用）已统一写 `/api/category/tree` 这种全路径。当 `VITE_API_BASE_URL` 为空字符串（生产环境）时，`import.meta.env.VITE_API_BASE_URL || '/api'` 兜底返回 `/api`，最终请求变成 `/api/api/category/tree`，全部 404。

- [ ] **Step 1: 修改 request.js**

把第 6 行 `baseURL` 兜底从 `/api` 改成 `''`（空字符串）：

```javascript
import axios from 'axios'

let isRedirecting = false

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器：添加token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一处理ResultVO格式
request.interceptors.response.use(
  response => {
    const resData = response.data
    // 处理204或非JSON响应
    if (!resData || typeof resData !== 'object') {
      return response
    }

    const { code, msg, data } = resData
    if (code === 200) {
      return data
    }
    // 业务错误
    const error = new Error(msg || '请求失败')
    error.code = code
    return Promise.reject(error)
  },
  error => {
    // HTTP错误
    if (error.response?.status === 401) {
      if (!isRedirecting) {
        isRedirecting = true
        localStorage.removeItem('token')
        localStorage.removeItem('username')
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default request
```

- [ ] **Step 2: 验证 .env.production 配置**

确认 `/Users/processmonitor/Documents/WebStormProjects/xianwei_web/frontend/.env.production` 内容是：

```bash
VITE_API_BASE_URL=
VITE_APP_TITLE=献微系统
```

如果不是，直接覆盖为上述内容。

- [ ] **Step 3: 本地构建验证**

```bash
cd /Users/processmonitor/Documents/WebStormProjects/xianwei_web/frontend
npm install
npm run build
```

Expected: `dist/index.html` 生成，无报错。

- [ ] **Step 4: 检查 dist 中是否含错误的 `/api/api/` 字样**

```bash
grep -r "/api/api/" /Users/processmonitor/Documents/WebStormProjects/xianwei_web/frontend/dist/ 2>/dev/null
```

Expected: 无输出（grep 返回 exit 1）。说明没有路径重复 bug。

- [ ] **Step 5: 提交**

```bash
cd /Users/processmonitor/Documents/WebStormProjects/xianwei_web
git add frontend/src/api/request.js frontend/.env.production
git commit -m "fix(api): baseURL 兜底改空字符串，避免与业务代码 /api 前缀重复"
```

---

## Task 2：编写 nginx 站点配置

**Files:**
- Create: `/Users/processmonitor/Downloads/176-deploy/frontend/nginx.conf`

- [ ] **Step 1: 创建 frontend 目录**

```bash
mkdir -p /Users/processmonitor/Downloads/176-deploy/frontend/dist
```

Expected: 目录已存在。

- [ ] **Step 2: 写入 nginx.conf**

文件路径：`/Users/processmonitor/Downloads/176-deploy/frontend/nginx.conf`

内容：

```nginx
server {
    listen 80;
    server_name _;
    client_max_body_size 100M;

    root /usr/share/nginx/html;
    index index.html;

    # 关闭 sendfile（虚拟机/挂载卷场景更稳定）
    sendfile off;

    # Vue SPA history 路由兜底
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 反代后端 API（覆盖所有后端控制器前缀）
    location ~ ^/(api|extraction|test|actuator|druid|swagger-ui|v3)/ {
        proxy_pass http://backend:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
        proxy_connect_timeout 30s;
    }

    # nginx 自身健康检查
    location /nginx-health {
        access_log off;
        return 200 'ok';
        add_header Content-Type text/plain;
    }

    # 静态资源缓存策略
    location ~ ^/assets/.*\.(js|css|png|jpg|jpeg|gif|svg|woff2?|ttf)$ {
        expires 7d;
        add_header Cache-Control "public, immutable";
    }
}
```

- [ ] **Step 3: 写一个占位 index.html（方便首次启动验证）**

```bash
cat > /Users/processmonitor/Downloads/176-deploy/frontend/dist/index.html <<'HTML'
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>placeholder</title></head>
<body>frontend dist placeholder, waiting for upload</body>
</html>
HTML
```

- [ ] **Step 4: 提交**

```bash
cd /Users/processmonitor/Downloads/176-deploy
# 此目录不在 git，跳过 commit；改用 backup 备注
echo "本目录在 ~/Downloads/176-deploy，不入 git，但配置文件通过 manage.sh 同步到 176" > frontend/README.md
```

无 commit 步骤（176-deploy 不在 git 仓库内）。

---

## Task 3：扩展 docker-compose.yml 增加 nginx 服务

**Files:**
- Modify: `/Users/processmonitor/Downloads/176-deploy/docker-compose.yml`

- [ ] **Step 1: 读取当前 docker-compose.yml**

```bash
cat /Users/processmonitor/Downloads/176-deploy/docker-compose.yml
```

记下现有的 backend / embedding / elasticsearch / networks / volumes 段。

- [ ] **Step 2: 在 services 段末（networks 段之前）追加 nginx 服务**

打开 `/Users/processmonitor/Downloads/176-deploy/docker-compose.yml`，在 `services:` 区块末尾追加：

```yaml

  # 前端静态服务 + 同源反代 backend
  nginx:
    image: nginx:1.25-alpine
    container_name: xianwei-nginx
    restart: always
    depends_on:
      - backend
    ports:
      - "8527:80"
    volumes:
      - ./frontend/dist:/usr/share/nginx/html:ro
      - ./frontend/nginx.conf:/etc/nginx/conf.d/default.conf:ro
    networks:
      - xianwei-net
```

注意缩进与现有 `backend:` 等服务保持一致（2 空格缩进 services 下、4 空格缩进字段）。

- [ ] **Step 3: 验证 yaml 语法**

```bash
docker compose -f /Users/processmonitor/Downloads/176-deploy/docker-compose.yml config --quiet
```

Expected: 无输出（语法 OK）。

- [ ] **Step 4: 无 commit**

176-deploy 不在 git。

---

## Task 4：拉取 nginx 镜像并导出为 tar

**Files:**
- Create: `/Users/processmonitor/Downloads/176-deploy/containers/nginx-image.tar`

- [ ] **Step 1: Mac 端拉取 nginx:1.25-alpine 镜像**

```bash
docker pull nginx:1.25-alpine
```

Expected: 镜像下载成功，约 40 MB。

- [ ] **Step 2: 验证镜像架构匹配 176（arm64）**

```bash
docker image inspect nginx:1.25-alpine --format '{{.Architecture}} {{.Os}}'
```

Expected: `arm64 linux`。如果显示 `amd64`，重新执行：
```bash
docker pull --platform linux/arm64 nginx:1.25-alpine
```

- [ ] **Step 3: 导出为 tar**

```bash
docker save nginx:1.25-alpine -o /Users/processmonitor/Downloads/176-deploy/containers/nginx-image.tar
ls -lh /Users/processmonitor/Downloads/176-deploy/containers/nginx-image.tar
```

Expected: 文件约 40 MB。

- [ ] **Step 4: 无 commit**

176-deploy 不在 git。

---

## Task 5：扩展 manage.sh 加入 nginx 服务支持

**Files:**
- Modify: `/Users/processmonitor/Downloads/176-deploy/manage.sh`

- [ ] **Step 1: 在服务定义段加 nginx**

打开 `/Users/processmonitor/Downloads/176-deploy/manage.sh`，找到 `SERVICES_UP=`、`SERVICES_DOWN=`、`IMAGES=`、`HEALTH_URLS=`、`PORTS=`，按下列修改：

把原本：

```bash
SERVICES_UP=("xianwei-es" "xianwei-embedding" "xianwei-backend")
SERVICES_DOWN=("xianwei-backend" "xianwei-embedding" "xianwei-es")
IMAGES=("elasticsearch:7.17.15" "xianwei-embedding:latest" "xianwei-backend:latest")
```

改为：

```bash
SERVICES_UP=("xianwei-es" "xianwei-embedding" "xianwei-backend" "xianwei-nginx")
SERVICES_DOWN=("xianwei-nginx" "xianwei-backend" "xianwei-embedding" "xianwei-es")
IMAGES=("elasticsearch:7.17.15" "xianwei-embedding:latest" "xianwei-backend:latest" "nginx:1.25-alpine")
```

把 `HEALTH_URLS` 数组加一行：

```bash
declare -A HEALTH_URLS=(
    [xianwei-es]="http://localhost:9200"
    [xianwei-embedding]="http://localhost:5002/health"
    [xianwei-backend]="http://localhost:8081/actuator/health"
    [xianwei-nginx]="http://localhost:8527/nginx-health"
)
```

把 `PORTS` 数组加一行：

```bash
declare -A PORTS=(
    [xianwei-es]="9200"
    [xianwei-embedding]="5002"
    [xianwei-backend]="8081"
    [xianwei-nginx]="8527"
)
```

- [ ] **Step 2: 找到 `_load_if_missing` 调用处，加 nginx**

找到现有 install 流程中调用 `_load_if_missing` 的地方（搜索 `_load_if_missing "xianwei-backend:latest"`），在其后追加一行：

```bash
[[ "${SVC_DECISION[xianwei-nginx]}" == "load-new" ]] && \
    _load_if_missing "nginx:1.25-alpine" "${CONTAINERS_DIR}/nginx-image.tar"
```

- [ ] **Step 3: 找到智能检测 `_detect_service` 调用段，加 nginx**

在原有 3 个 `_detect_service` 调用之后追加：

```bash
_detect_service "xianwei-nginx"     "nginx:1.25-alpine"        "^nginx:" 8527
```

注意：这里端口 `8527` 是我们的，**lijunzuo 的 nginx 用的是 8526**，不冲突。如果检测到 8527 已被占用，脚本会自动跳过。

- [ ] **Step 4: 加 `cmd_deploy_frontend` 子命令**

在 `cmd_update` 函数定义之前（或之后任意位置）追加新函数：

```bash
cmd_deploy_frontend() {
    require_docker
    log "部署前端..."

    local dist_index="${ROOT}/frontend/dist/index.html"
    local nginx_conf="${ROOT}/frontend/nginx.conf"

    [[ -f "$dist_index" ]] || fail "未发现 ${dist_index}，请先 rsync 上传 dist"
    [[ -f "$nginx_conf" ]] || fail "未发现 ${nginx_conf}"

    if container_running xianwei-nginx; then
        log "热加载 nginx 配置和静态资源..."
        docker exec xianwei-nginx nginx -t || fail "nginx 配置语法错误"
        docker exec xianwei-nginx nginx -s reload
    else
        log "首次启动 xianwei-nginx..."
        detect_compose
        cd "$ROOT"
        $COMPOSE_CMD up -d --no-deps --pull never nginx 2>&1 | tail -3
        sleep 3
    fi

    info "等待 nginx 健康..."
    wait_health "xianwei-nginx" "${HEALTH_URLS[xianwei-nginx]}" 30 || true

    log "✅ 前端发布完成: http://localhost:${PORTS[xianwei-nginx]}"
}
```

- [ ] **Step 5: 在主 `case "$1" in` 派发段加入 `deploy-frontend`**

找到入口 `case "$1" in install) cmd_install ;;` 段，加入：

```bash
        deploy-frontend) cmd_deploy_frontend ;;
```

放在 `update)` 后面、`-h|--help|help)` 前面。

- [ ] **Step 6: 帮助文本中加一行**

找到 `-h|--help|help)` 的 cat <<EOF 块，在 `update     ...` 那行下面加：

```
  deploy-frontend  发布前端 dist（首次启容器；后续热加载）
```

- [ ] **Step 7: 语法检查**

```bash
bash -n /Users/processmonitor/Downloads/176-deploy/manage.sh && echo "✅ 语法 OK"
```

Expected: `✅ 语法 OK`。

- [ ] **Step 8: 无 commit**

176-deploy 不在 git。

---

## Task 6：编写 Mac 端 upload-frontend.sh

**Files:**
- Create: `/Users/processmonitor/Documents/WebStormProjects/xianwei_web/scripts/upload-frontend.sh`

- [ ] **Step 1: 建 scripts 目录**

```bash
mkdir -p /Users/processmonitor/Documents/WebStormProjects/xianwei_web/scripts
```

- [ ] **Step 2: 写入脚本**

文件：`/Users/processmonitor/Documents/WebStormProjects/xianwei_web/scripts/upload-frontend.sh`

内容：

```bash
#!/usr/bin/env bash
# Mac 端：构建前端 + rsync 增量上传 + nginx reload
set -euo pipefail

FRONTEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/frontend"
REMOTE_USER="yinbanghu"
REMOTE_HOST="36.141.21.176"
REMOTE_PORT="1111"
REMOTE_PASS="QY@qy1314"
REMOTE_DIST="~/176-deploy/frontend/dist/"
ACCESS_URL="http://${REMOTE_HOST}:8527"

# 颜色
GREEN='\033[1;32m'; YELLOW='\033[1;33m'; RED='\033[1;31m'; NC='\033[0m'
log()  { printf "${GREEN}[INFO]${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}[WARN]${NC} %s\n" "$*"; }
fail() { printf "${RED}[FAIL]${NC} %s\n" "$*" >&2; exit 1; }

# 1. 检查环境
[[ -d "$FRONTEND_DIR" ]] || fail "frontend 目录不存在: $FRONTEND_DIR"
command -v npm     >/dev/null 2>&1 || fail "未检测到 npm"
command -v rsync   >/dev/null 2>&1 || fail "未检测到 rsync"
command -v expect  >/dev/null 2>&1 || fail "未检测到 expect（brew install expect）"

# 2. 构建
log "1/3 构建 dist..."
cd "$FRONTEND_DIR"
npm run build
[[ -f "dist/index.html" ]] || fail "构建失败，未生成 dist/index.html"

# 3. rsync 增量上传
log "2/3 rsync 同步到 176..."
/usr/bin/expect <<EOF
set timeout -1
spawn rsync -avz --delete -e "ssh -o StrictHostKeyChecking=no -p ${REMOTE_PORT}" \
    "${FRONTEND_DIR}/dist/" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIST}"
expect {
    "*password:*" { send "${REMOTE_PASS}\r"; exp_continue }
    eof
}
EOF

# 4. nginx reload
log "3/3 reload nginx..."
/usr/bin/expect <<EOF
set timeout 60
spawn ssh -o StrictHostKeyChecking=no -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} "docker exec xianwei-nginx nginx -t && docker exec xianwei-nginx nginx -s reload"
expect {
    "*password:*" { send "${REMOTE_PASS}\r" }
    timeout { puts "TIMEOUT"; exit 1 }
}
expect eof
EOF

log "✅ 发布完成: ${ACCESS_URL}"
```

- [ ] **Step 3: 加执行权限**

```bash
chmod +x /Users/processmonitor/Documents/WebStormProjects/xianwei_web/scripts/upload-frontend.sh
```

- [ ] **Step 4: 加 .gitignore（防止密码进 git）**

打开 `/Users/processmonitor/Documents/WebStormProjects/xianwei_web/.gitignore` 末尾加入：

```
# Mac 端发布脚本含密码，不入 git
scripts/upload-frontend.sh
```

- [ ] **Step 5: 语法检查**

```bash
bash -n /Users/processmonitor/Documents/WebStormProjects/xianwei_web/scripts/upload-frontend.sh && echo "✅ 语法 OK"
```

Expected: `✅ 语法 OK`。

- [ ] **Step 6: 提交 .gitignore（脚本本身不入 git）**

```bash
cd /Users/processmonitor/Documents/WebStormProjects/xianwei_web
git add .gitignore
git commit -m "chore: ignore scripts/upload-frontend.sh（含密码）"
```

---

## Task 7：首次上传 nginx 镜像 + frontend 配置到 176

**Files:** 无（远程操作）

- [ ] **Step 1: 上传 nginx-image.tar 到 176**

```bash
/usr/bin/expect <<'EOF'
set timeout -1
spawn scp -o StrictHostKeyChecking=no -P 1111 \
    /Users/processmonitor/Downloads/176-deploy/containers/nginx-image.tar \
    yinbanghu@36.141.21.176:~/176-deploy/containers/nginx-image.tar
expect {
    "*password:*" { send "QY@qy1314\r"; exp_continue }
    eof
}
EOF
```

Expected: 100% 上传完成，约 40MB。

- [ ] **Step 2: 同步 docker-compose.yml**

```bash
/usr/bin/expect <<'EOF'
set timeout 60
spawn scp -o StrictHostKeyChecking=no -P 1111 \
    /Users/processmonitor/Downloads/176-deploy/docker-compose.yml \
    yinbanghu@36.141.21.176:~/176-deploy/docker-compose.yml
expect {
    "*password:*" { send "QY@qy1314\r" }
    timeout { puts "TIMEOUT"; exit 1 }
}
expect eof
EOF
```

Expected: docker-compose.yml 100% 上传。

- [ ] **Step 3: 同步 manage.sh**

```bash
/usr/bin/expect <<'EOF'
set timeout 60
spawn scp -o StrictHostKeyChecking=no -P 1111 \
    /Users/processmonitor/Downloads/176-deploy/manage.sh \
    yinbanghu@36.141.21.176:~/176-deploy/manage.sh
expect {
    "*password:*" { send "QY@qy1314\r" }
    timeout { puts "TIMEOUT"; exit 1 }
}
expect eof
EOF
```

- [ ] **Step 4: 同步 nginx.conf 和占位 dist**

```bash
/usr/bin/expect <<'EOF'
set timeout 60
spawn scp -o StrictHostKeyChecking=no -P 1111 -r \
    /Users/processmonitor/Downloads/176-deploy/frontend \
    yinbanghu@36.141.21.176:~/176-deploy/
expect {
    "*password:*" { send "QY@qy1314\r" }
    timeout { puts "TIMEOUT"; exit 1 }
}
expect eof
EOF
```

- [ ] **Step 5: 在 176 上加载镜像**

```bash
/usr/bin/expect <<'EOF'
set timeout 120
log_user 1
spawn ssh -o StrictHostKeyChecking=no -p 1111 yinbanghu@36.141.21.176 \
    "docker load -i ~/176-deploy/containers/nginx-image.tar && docker images | grep nginx | head -3"
expect {
    "*password:*" { send "QY@qy1314\r" }
    timeout { puts "TIMEOUT"; exit 1 }
}
expect eof
EOF
```

Expected: 输出含 `Loaded image: nginx:1.25-alpine`。

- [ ] **Step 6: 启动 nginx 容器**

```bash
/usr/bin/expect <<'EOF'
set timeout 60
log_user 1
spawn ssh -o StrictHostKeyChecking=no -p 1111 yinbanghu@36.141.21.176 \
    "cd ~/176-deploy && chmod +x manage.sh && ./manage.sh deploy-frontend"
expect {
    "*password:*" { send "QY@qy1314\r" }
    timeout { puts "TIMEOUT"; exit 1 }
}
expect eof
EOF
```

Expected: 输出 `✅ 前端发布完成: http://localhost:8527`。

- [ ] **Step 7: 验证占位页可访问**

```bash
/usr/bin/expect <<'EOF'
set timeout 30
log_user 1
spawn ssh -o StrictHostKeyChecking=no -p 1111 yinbanghu@36.141.21.176 \
    "curl -s http://localhost:8527/nginx-health && echo && curl -s http://localhost:8527/ | head -c 200"
expect {
    "*password:*" { send "QY@qy1314\r" }
    timeout { puts "TIMEOUT"; exit 1 }
}
expect eof
EOF
```

Expected:
```
ok
<!DOCTYPE html>... frontend dist placeholder ...
```

- [ ] **Step 8: 验证 nginx 反代 backend 通**

```bash
/usr/bin/expect <<'EOF'
set timeout 30
log_user 1
spawn ssh -o StrictHostKeyChecking=no -p 1111 yinbanghu@36.141.21.176 \
    "curl -s http://localhost:8527/actuator/health"
expect {
    "*password:*" { send "QY@qy1314\r" }
    timeout { puts "TIMEOUT"; exit 1 }
}
expect eof
EOF
```

Expected: `{"status":"UP"}`。说明 nginx → backend 反代成功。

---

## Task 8：浏览器端首次访问验证

**Files:** 无

- [ ] **Step 1: 浏览器访问 http://36.141.21.176:8527/**

期望看到占位文本「frontend dist placeholder, waiting for upload」。

- [ ] **Step 2: 浏览器访问 http://36.141.21.176:8527/actuator/health**

期望看到 `{"status":"UP"}`。

- [ ] **Step 3: 浏览器访问 http://36.141.21.176:8527/api/rag/index/status**

期望看到 RAG 索引状态 JSON（之前 Task 35 已修复 BigDecimal bug）。

如果上述全部通过，说明 nginx 容器和反代都已工作。

---

## Task 9：用 upload-frontend.sh 部署真实前端

**Files:** 无（执行脚本）

- [ ] **Step 1: 在本地修改前端密码字段（一次性）**

打开 `/Users/processmonitor/Documents/WebStormProjects/xianwei_web/scripts/upload-frontend.sh`，确认顶部变量值与你环境匹配：

```bash
REMOTE_USER="yinbanghu"
REMOTE_HOST="36.141.21.176"
REMOTE_PORT="1111"
REMOTE_PASS="QY@qy1314"
```

如果未来密码变了，只改这一处。

- [ ] **Step 2: 执行脚本**

```bash
bash /Users/processmonitor/Documents/WebStormProjects/xianwei_web/scripts/upload-frontend.sh
```

Expected: 三段输出依次完成：
```
[INFO] 1/3 构建 dist...
[INFO] 2/3 rsync 同步到 176...
[INFO] 3/3 reload nginx...
[INFO] ✅ 发布完成: http://36.141.21.176:8527
```

- [ ] **Step 3: 浏览器访问 http://36.141.21.176:8527**

期望看到真实前端首页（不是占位文本）。

如果白屏 → F12 看 console。常见问题：
- 路径 404：检查 nginx.conf 的 `try_files`
- 接口 404：检查 baseURL（应该是空，不是 `/api`）
- 接口 CORS：不应该出现，如果出现说明前端用了绝对 URL，再次检查 .env

---

## Task 10：联调阶段 1 - 基础 CRUD 验证

**Files:** 无（手工验证）

按下面顺序在浏览器逐个验证，每项记录通过/失败：

- [ ] **Step 1: 首页加载，登录 mock**
  - 操作：访问 `http://36.141.21.176:8527/`，点击「登录」（任意用户名/密码）
  - Expected: localStorage 出现 token，跳转到首页

- [ ] **Step 2: 配置加载**
  - 操作：F12 Network 看 `/api/uygur/config` 响应
  - Expected: 返回 ResultVO 包含 `minioPrefix` 字段

- [ ] **Step 3: 分类树**
  - 操作：进入分类管理页 / 触发 `/api/category/tree`
  - Expected: 返回树状结构，至少包含 id=2 的「未分类」

- [ ] **Step 4: 报文分页查询**
  - 操作：进入报文列表页，分页查询
  - Expected: 返回总数 ~103870、列表内容（与 Task 35 验证的 totalDocs 一致）

- [ ] **Step 5: 看板数据**
  - 操作：进入看板页
  - Expected: `/api/dashboard/overview` 等接口返回有效数据

- [ ] **Step 6: 库管 DDL**
  - 操作：库管页查询 `origin_text` 表结构
  - Expected: 返回字段列表

- [ ] **Step 7: 整理通过率**

```
阶段 1 通过率：__ / 6
不通过的接口列表：
- ...
```

如有失败，按故障排查清单（设计文档第 8 节）逐一定位，修完再继续阶段 2。

---

## Task 11：联调阶段 2 - 算法链路验证

**Files:** 无（手工验证）

前置：`xianwei-algorithm` 容器在 9203 端口运行（已确认）。

- [ ] **Step 1: 属性抽取**
  - 操作：从报文列表选一条，点击「抽取属性」
  - Expected: 后端调 `host.docker.internal:9203/extract`，返回结果，`extraction_result` 表入新记录

- [ ] **Step 2: 事件分析**
  - 操作：触发某日批量事件分析
  - Expected: `event_analysis` 表入记录

- [ ] **Step 3: 目标分析**
  - 操作：对某报文触发目标分析
  - Expected: `target_analysis` 表入记录

- [ ] **Step 4: 算法日志侧验证**

```bash
/usr/bin/expect <<'EOF'
set timeout 30
log_user 1
spawn ssh -o StrictHostKeyChecking=no -p 1111 yinbanghu@36.141.21.176 \
    "docker logs --tail 30 xianwei-algorithm 2>&1 | tail -10"
expect {
    "*password:*" { send "QY@qy1314\r" }
    timeout { puts "TIMEOUT"; exit 1 }
}
expect eof
EOF
```

Expected: 看到对应请求的访问日志。

- [ ] **Step 5: 通过率汇总**

```
阶段 2 通过率：__ / 3
失败接口：
- ...
```

---

## Task 12：联调阶段 3 - 融合 & 导出验证

**Files:** 无（手工验证）

- [ ] **Step 1: 多报融合**
  - 操作：勾选 3-5 条报文，触发融合
  - Expected: `/api/fusion/create` 返回融合 ID，`fusion_report` 表入记录

- [ ] **Step 2: PDF 导出**
  - 操作：从融合报告点击「导出 PDF」
  - Expected: 浏览器下载 PDF 文件，可打开

- [ ] **Step 3: 目标融合**
  - 操作：勾选多个目标，触发目标融合
  - Expected: `target_fusion` 表入记录

- [ ] **Step 4: 通过率汇总**

```
阶段 3 通过率：__ / 3
失败接口：
- ...
```

---

## Task 13：联调阶段 4 - RAG 验证

**Files:** 无（手工验证）

- [ ] **Step 1: docx 上传**
  - 操作：上传一个测试 docx 文件
  - Expected: 文件入 MinIO，`rag_document` 表入记录

- [ ] **Step 2: 触发索引**
  - 操作：触发 RAG 索引（指定时间段）
  - Expected: ES `xianwei_docs` 索引文档数增加

- [ ] **Step 3: 语义检索**
  - 操作：输入查询关键词
  - Expected: 返回 top-k 相关结果

- [ ] **Step 4: Embedding 服务侧验证**

```bash
/usr/bin/expect <<'EOF'
set timeout 30
log_user 1
spawn ssh -o StrictHostKeyChecking=no -p 1111 yinbanghu@36.141.21.176 \
    "curl -s -X POST http://localhost:5002/embed -H 'Content-Type: application/json' -d '{\"text\":\"测试\"}' | head -c 200"
expect {
    "*password:*" { send "QY@qy1314\r" }
    timeout { puts "TIMEOUT"; exit 1 }
}
expect eof
EOF
```

Expected: 返回向量 JSON 片段。

- [ ] **Step 5: 通过率汇总**

```
阶段 4 通过率：__ / 3
失败接口：
- ...
```

---

## Task 14：编写迁移到 72 的 checklist

**Files:**
- Create: `~/Downloads/176-deploy/docs/migrate-to-72.md`

- [ ] **Step 1: 写迁移文档**

文件路径：`/Users/processmonitor/Downloads/176-deploy/docs/migrate-to-72.md`

内容：

```markdown
# 从 176 联调环境迁移到 72 离线生产

## 1. 复制部署包

```bash
# 在 176 上打包当前 176-deploy（含已运行的镜像 + 配置）
cd ~
tar czf production-deploy-$(date +%Y%m%d).tar.gz 176-deploy/

# scp 到中转机，再刻盘到 72
scp -P 1111 production-deploy-*.tar.gz user@中转机:/...
```

## 2. 在 72 上解压

```bash
cd /opt
tar xzf production-deploy-*.tar.gz
cd 176-deploy
mv ../176-deploy ../prod-deploy  # 改名（可选）
```

## 3. 修改环境变量

打开 `docker-compose.yml`，调整以下字段（按 72 实际环境）：

- `DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD`
- `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`
- `ES_HOST` / `SPRING_ELASTICSEARCH_URIS`（如果 72 用本机 ES，可改为 `elasticsearch:9200`）
- `ALGORITHM_SERVICE_URL`（如果 72 上算法走容器名，改为 `http://xianwei-algorithm:5001`）

## 4. 算法服务

如果 72 上需要部署算法服务：
- 把 `xianwei-algorithm:offline` 镜像 docker save 到 tar，加入部署包
- 在 docker-compose.yml services 段加入算法服务定义

## 5. 启动

```bash
./manage.sh install
./manage.sh status
```

## 6. 验证

按本目录 `docs/api-examples.md` 跑一次冒烟测试。
```

- [ ] **Step 2: 无 commit**

176-deploy 不在 git。

---

## Task 15：汇总联调结果

**Files:**
- Create: `~/Downloads/176-deploy/docs/integration-test-report.md`

- [ ] **Step 1: 写报告模板**

文件路径：`/Users/processmonitor/Downloads/176-deploy/docs/integration-test-report.md`

内容：

```markdown
# 三端联调验收报告

**日期**: 2026-06-XX
**环境**: 176 服务器
**入口**: http://36.141.21.176:8527

## 服务状态

| 服务 | 状态 | 端口 |
|---|---|---|
| xianwei-nginx | 运行中 | 8527 |
| xianwei-backend | 运行中 | 8081 |
| xianwei-embedding | 运行中 | 5002 |
| es_ybh（复用） | 运行中 | 9200 |
| xianwei-algorithm（复用） | 运行中 | 9203 |

## 阶段通过率

- 阶段 1 (CRUD)：__ / 6
- 阶段 2 (算法)：__ / 3
- 阶段 3 (融合)：__ / 3
- 阶段 4 (RAG)：__ / 3

## 已知问题

- ...

## 待迁移到 72 的事项

- ...
```

- [ ] **Step 2: 无 commit**

176-deploy 不在 git。

---

## Self-Review

我对照 spec 第 1-12 节做了如下检查：

### Spec 覆盖

- ✅ §2 总体架构 → Task 3 + Task 5（compose + manage.sh）
- ✅ §3 容器编排 → Task 3
- ✅ §4 nginx.conf → Task 2
- ✅ §5.1 .env.production → Task 1 Step 2
- ✅ §5.3 baseURL bug → Task 1（设计文档明确点名要修，已落到独立 Task）
- ✅ §6.1 manage.sh deploy-frontend → Task 5
- ✅ §6.2 upload-frontend.sh → Task 6
- ✅ §6.3 首次部署 → Task 7
- ✅ §7 联调验证 4 阶段 → Task 10/11/12/13
- ✅ §9 迁移到 72 → Task 14
- ✅ §10 验收标准 → Task 15

### 占位符扫描

无 TBD/TODO/"implement later"。Task 14、15 是模板填空（XX 是日期、__ 是数字），属于用户在执行时记录的内容，不是占位符 bug。

### 类型一致性

- `xianwei-nginx` 容器名、`nginx:1.25-alpine` 镜像、`8527` 端口、`/nginx-health` 路径在 Task 2/3/4/5/7/8 中完全一致。
- `~/176-deploy/frontend/dist/` 路径在 Task 2/5/6/7 一致。
- `manage.sh` 内部函数 `_load_if_missing` / `_detect_service` / `wait_health` / `detect_compose` 与现有 manage.sh 实现一致（已通过 grep 验证）。

### 修复

无需修改。

---

## 备注

- **本计划不创建任何 git 仓库**：176-deploy 不在 git，前端仓库只 commit 必要修改（Task 1 / Task 6 .gitignore）。
- **密码硬编码**：Mac 端 upload-frontend.sh 含明文密码，已加 .gitignore。后续可考虑 ssh key 替换。
- **任务依赖**：Task 1-6 可并行准备；Task 7 依赖 1-6；Task 8 依赖 7；Task 9 依赖 7；Task 10-13 依赖 9（前端真实部署后才能测）；Task 14-15 在联调过程中持续更新。

---

**计划完成。**
