# Claude Code 容器离线部署说明

## 目标环境

- 目标机器：离线环境，无法访问外网
- 架构要求：ARM64 (aarch64)
- 需要Docker环境

---

## 准备工作（在有网络的机器上执行）

### 1. 导出镜像

在有网络的服务器上导出镜像：

```bash
# 登录源服务器
ssh -i ~/.ssh/id_rsa_ascend910b -p 1111 yinbanghu@36.141.21.176

# 导出镜像为tar文件
docker save claude-code:arm64 -o claude-code-arm64.tar

# 查看导出文件大小
ls -lh claude-code-arm64.tar
# 约 1.95GB
```

### 2. 导出配置卷数据（可选）

如果需要保留配置和工作数据：

```bash
# 导出workspace卷数据
docker run --rm -v claude-workspace:/data -v $(pwd):/backup alpine tar -cvf /backup/claude-workspace.tar /data

# 导出config卷数据  
docker run --rm -v claude-config:/data -v $(pwd):/backup alpine tar -cvf /backup/claude-config.tar /data
```

---

## 需传输的文件

| 文件 | 大小 | 说明 |
|------|------|------|
| `claude-code-arm64.tar` | ~2GB | Docker镜像文件 |
| `claude-workspace.tar` | 可选 | 工作目录数据 |
| `claude-config.tar` | 可选 | 配置文件数据 |

---

## 目标机器部署步骤

### 步骤1：传输文件

使用scp、USB或其他方式将文件传输到目标机器：

```bash
# 示例：使用scp传输（如果目标机器可达）
scp claude-code-arm64.tar user@target_ip:~/claude-code/
```

### 步骤2：导入镜像

```bash
# 在目标机器上执行
docker load -i claude-code-arm64.tar

# 验证镜像导入成功
docker images | grep claude-code
```

### 步骤3：创建数据卷（可选）

```bash
# 创建卷
docker volume create claude-workspace
docker volume create claude-config

# 导入卷数据（如果有）
docker run --rm -v claude-workspace:/data -v $(pwd):/backup alpine tar -xvf /backup/claude-workspace.tar -C /
docker run --rm -v claude-config:/data -v $(pwd):/backup alpine tar -xvf /backup/claude-config.tar -C /
```

### 步骤4：启动容器

```bash
docker run -d \
  --name claude-code \
  -p 8080:8080 \
  -v claude-workspace:/workspace \
  -v claude-config:/home/node/.claude \
  -e ANTHROPIC_MODEL=GLM-5 \
  -e ANTHROPIC_BASE_URL=https://llmapi.paratera.com \
  -e ANTHROPIC_AUTH_TOKEN=your-api-key \
  -e NODE_OPTIONS=--max-old-space-size=4096 \
  -e TZ=Asia/Shanghai \
  claude-code:arm64 \
  sleep infinity
```

**重要参数说明**：

| 参数 | 说明 |
|------|------|
| `-p 8080:8080` | 端口映射，外部访问端口 |
| `-v claude-workspace:/workspace` | 工作目录挂载 |
| `-v claude-config:/home/node/.claude` | 配置目录挂载 |
| `ANTHROPIC_MODEL` | 使用的模型名称 |
| `ANTHROPIC_BASE_URL` | API服务地址（需目标机器可访问） |
| `ANTHROPIC_AUTH_TOKEN` | API密钥（需要修改） |

### 步骤5：验证部署

```bash
# 检查容器状态
docker ps | grep claude-code

# 测试访问
curl http://localhost:8080
```

### 步骤6：测试API连接

在启动容器前或部署后，测试本地大模型API是否可用：

```bash
# 测试API地址连通性
curl -s -o /dev/null -w "%{http_code}" <API_BASE_URL>/v1/models

# 示例：测试GLM-5 API
curl -s https://llmapi.paratera.com/v1/models

# 测试完整API调用（验证模型和密钥）
curl -X POST <API_BASE_URL>/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <API_KEY>" \
  -d '{
    "model": "<MODEL_NAME>",
    "messages": [{"role": "user", "content": "你好"}],
    "max_tokens": 10
  }'

# 示例：测试GLM-5模型
curl -X POST https://llmapi.paratera.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-K0_Bo4nf8yhgEk9RQsXsWg" \
  -d '{
    "model": "GLM-5",
    "messages": [{"role": "user", "content": "你好"}],
    "max_tokens": 10
  }'

http://36.103.234.242:8122/

curl -X POST http://36.103.234.242:8122/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-K0_Bo4nf8yhgEk9RQsXsWg" \
  -d '{
    "model": "Qwen3.5-122B-A10B-FP8",
    "messages": [{"role": "user", "content": "你好"}],
    "max_tokens": 10
  }'
```

**预期结果**：
- HTTP状态码应为 `200`
- 返回JSON格式的响应，包含模型信息或对话内容

**如果测试失败**：
- 检查API地址是否正确（注意是否需要 `/v1` 后缀）
- 检查API密钥是否有效
- 检查模型名称是否匹配
- 检查目标机器是否能访问该API地址

---

## 注意事项

### 1. API服务地址

离线机器需要能够访问API服务地址。如果API服务也在内网，需要：

```bash
# 修改为内网API地址
-e ANTHROPIC_BASE_URL=http://internal-api-server:port/v1
```

### 2. API密钥

需要根据实际API服务配置正确的密钥：

```bash
-e ANTHROPIC_AUTH_TOKEN=your-actual-api-key
```

### 3. 架构兼容性

确保目标机器架构为ARM64：
```bash
uname -m
# 输出应为: aarch64
```

如果是x86_64架构，需要使用x86版本的镜像。

### 4. 端口冲突

如果8080端口已被占用，可修改为其他端口：
```bash
-p 8518:8080  # 使用8518端口
```

---

## 管理命令

### 查看日志
```bash
docker logs claude-code
docker logs -f claude-code  # 实时查看
```

### 进入容器
```bash
docker exec -it claude-code bash
```

### 重启容器
```bash
docker restart claude-code
```

### 停止容器
```bash
docker stop claude-code
```

### 删除容器
```bash
docker rm -f claude-code
```

---

## 完整部署脚本

创建 `deploy_claude_code.sh` 脚本：

```bash
#!/bin/bash
# Claude Code 容器离线部署脚本

# 配置参数（根据实际情况修改）
API_KEY="your-api-key"
API_URL="https://llmapi.paratera.com"
MODEL="GLM-5"
PORT="8080"

# 导入镜像
echo "正在导入镜像..."
docker load -i claude-code-arm64.tar

# 创建卷
echo "正在创建数据卷..."
docker volume create claude-workspace
docker volume create claude-config

# 启动容器
echo "正在启动容器..."
docker run -d \
  --name claude-code \
  -p ${PORT}:8080 \
  -v claude-workspace:/workspace \
  -v claude-config:/home/node/.claude \
  -e ANTHROPIC_MODEL=${MODEL} \
  -e ANTHROPIC_BASE_URL=${API_URL} \
  -e ANTHROPIC_AUTH_TOKEN=${API_KEY} \
  -e NODE_OPTIONS=--max-old-space-size=4096 \
  -e TZ=Asia/Shanghai \
  claude-code:arm64 \
  sleep infinity

# 验证部署
echo "验证部署..."
sleep 5
docker ps | grep claude-code
curl -s http://localhost:${PORT} > /dev/null && echo "部署成功！" || echo "部署失败，请检查日志"

echo "访问地址: http://$(hostname -I | awk '{print $1}'):${PORT}"
```

---

## 故障排查

### 1. 容器无法启动

检查镜像是否正确导入：
```bash
docker images | grep claude-code
```

### 2. 页面无法访问

检查容器状态和端口：
```bash
docker ps
netstat -tlnp | grep 8080
```

### 3. API调用失败

检查API地址和密钥配置：
```bash
docker exec claude-code env | grep ANTHROPIC
```

确保目标机器能够访问API服务地址。