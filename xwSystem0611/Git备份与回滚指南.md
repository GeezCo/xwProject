# Git 备份与回滚指南

## 一、Git 备份操作

### 1. 查看当前状态
```bash
cd "d:/项目开发/献微系统"
git status
```

### 2. 暂存所有修改
```bash
# 暂存所有修改和新增文件
git add .

# 或者选择性暂存
git add 文件路径
```

### 3. 提交到本地仓库
```bash
git commit -m "提交说明"

# 示例
git commit -m "feat: 离线部署优化与配置完善"
```

### 4. 推送到远程仓库（如果有）
```bash
# 推送到远程 main 分支
git push origin main

# 推送到远程 master 分支
git push origin master
```

### 5. 创建备份分支（推荐）
```bash
# 在重要操作前创建备份分支
git branch backup-20260517

# 查看所有分支
git branch -a
```

---

## 二、Git 回滚操作

### 方法 1：回滚到指定提交（保留工作区修改）
```bash
# 1. 查看提交历史
git log --oneline

# 输出示例：
# 1d3be2d (HEAD -> master) feat: 离线部署优化与配置完善
# d579272 feat: 报文融合功能完善与问题修复
# 7501527 初始化项目：献微系统

# 2. 回滚到指定提交（保留工作区修改）
git reset --soft <commit-hash>

# 示例：回滚到上一个提交
git reset --soft d579272
```

**效果**：
- 提交历史回退到指定版本
- 工作区文件保持不变
- 修改内容保留在暂存区，可重新提交

---

### 方法 2：回滚到指定提交（丢弃所有修改）⚠️
```bash
# 1. 查看提交历史
git log --oneline

# 2. 硬回滚到指定提交（危险操作！）
git reset --hard <commit-hash>

# 示例：回滚到上一个提交
git reset --hard d579272
```

**效果**：
- 提交历史回退到指定版本
- **工作区和暂存区的所有修改都会丢失**
- 代码完全恢复到指定提交时的状态

**⚠️ 警告**：此操作不可逆，除非使用 `git reflog` 恢复（见方法 5）

---

### 方法 3：撤销最近一次提交（保留修改）
```bash
# 撤销最近一次提交，修改保留在工作区
git reset --soft HEAD~1

# 或者撤销最近 N 次提交
git reset --soft HEAD~N
```

**使用场景**：
- 提交信息写错了，想重新提交
- 发现提交内容有遗漏，想补充后重新提交

---

### 方法 4：创建反向提交（推荐用于已推送的提交）
```bash
# 1. 查看提交历史
git log --oneline

# 2. 创建反向提交（撤销指定提交的修改）
git revert <commit-hash>

# 示例：撤销最近一次提交
git revert HEAD
```

**效果**：
- 不修改提交历史
- 创建一个新的提交，内容是撤销指定提交的修改
- 适合已经推送到远程的提交

---

### 方法 5：从 reflog 恢复误删的提交
```bash
# 1. 查看所有操作历史（包括已删除的提交）
git reflog

# 输出示例：
# 1d3be2d (HEAD -> master) HEAD@{0}: commit: feat: 离线部署优化与配置完善
# d579272 HEAD@{1}: commit: feat: 报文融合功能完善与问题修复
# 7501527 HEAD@{2}: commit (initial): 初始化项目：献微系统

# 2. 恢复到指定操作前的状态
git reset --hard HEAD@{N}

# 示例：恢复到 reflog 中的第 1 个状态
git reset --hard HEAD@{1}
```

**使用场景**：
- 执行了 `git reset --hard` 后后悔了
- 误删了重要提交，想找回来

**注意**：reflog 记录有时效性（默认保留 90 天）

---

## 三、常见场景与解决方案

### 场景 1：刚提交完发现代码有问题，想修改后重新提交
```bash
# 1. 撤销最近一次提交（保留修改）
git reset --soft HEAD~1

# 2. 修改代码
# ... 编辑文件 ...

# 3. 重新暂存和提交
git add .
git commit -m "修正后的提交信息"
```

---

### 场景 2：想回到昨天的版本，但保留今天的修改作为未提交状态
```bash
# 1. 查看提交历史，找到昨天的提交 hash
git log --oneline --since="1 day ago"

# 2. 软回滚到昨天的提交
git reset --soft <昨天的commit-hash>

# 3. 今天的修改会保留在暂存区，可以继续编辑
```

---

### 场景 3：想完全回到上周的版本，丢弃本周所有修改
```bash
# 1. 先创建备份分支（以防万一）
git branch backup-before-reset

# 2. 查看上周的提交
git log --oneline --since="1 week ago"

# 3. 硬回滚到上周的提交
git reset --hard <上周的commit-hash>

# 4. 如果后悔了，可以从备份分支恢复
git reset --hard backup-before-reset
```

---

### 场景 4：已经推送到远程，想撤销某次提交
```bash
# 不要使用 reset，使用 revert
git revert <commit-hash>

# 推送反向提交到远程
git push origin master
```

---

### 场景 5：误执行了 `git reset --hard`，想找回丢失的提交
```bash
# 1. 查看 reflog
git reflog

# 2. 找到丢失的提交对应的 HEAD@{N}
# 3. 恢复到那个状态
git reset --hard HEAD@{N}
```

---

## 四、最佳实践

### 1. 重要操作前先备份
```bash
# 创建备份分支
git branch backup-$(date +%Y%m%d)

# 或者创建带时间戳的标签
git tag backup-$(date +%Y%m%d-%H%M%S)
```

### 2. 定期推送到远程仓库
```bash
# 每天结束前推送
git push origin master
```

### 3. 使用有意义的提交信息
```bash
# 好的提交信息
git commit -m "feat: 添加离线部署功能"
git commit -m "fix: 修复算法服务健康检查问题"
git commit -m "docs: 更新数据库导入教程"

# 不好的提交信息
git commit -m "修改"
git commit -m "update"
```

### 4. 小步提交，频繁提交
- 每完成一个小功能就提交一次
- 不要等到一天结束才提交
- 便于回滚到任意功能点

### 5. 使用 `.gitignore` 排除不必要的文件
```bash
# 示例 .gitignore
*.log
*.tmp
node_modules/
target/
.idea/
*.pyc
__pycache__/
```

---

## 五、Git 命令速查表

| 命令 | 功能 |
|------|------|
| `git status` | 查看当前状态 |
| `git log --oneline` | 查看提交历史（简洁） |
| `git log --graph --all` | 查看分支图 |
| `git reflog` | 查看所有操作历史 |
| `git reset --soft HEAD~1` | 撤销最近一次提交（保留修改） |
| `git reset --hard HEAD~1` | 撤销最近一次提交（丢弃修改） |
| `git revert <hash>` | 创建反向提交 |
| `git branch <name>` | 创建分支 |
| `git checkout <branch>` | 切换分支 |
| `git diff` | 查看未暂存的修改 |
| `git diff --staged` | 查看已暂存的修改 |
| `git stash` | 暂存当前修改 |
| `git stash pop` | 恢复暂存的修改 |

---

## 六、当前项目备份记录

### 最新备份
- **提交 hash**: `1d3be2d`
- **提交信息**: feat: 离线部署优化与配置完善
- **提交时间**: 2026-05-17
- **主要内容**:
  - 创建算法服务离线镜像 xianwei-algorithm:offline (75MB)
  - 更新 docker-compose.yml 使用离线镜像和 python healthcheck
  - 修改 setup.sh 支持自动加载离线镜像
  - 增强 /health 接口返回 LLM 配置信息
  - 清理前端无用按钮（编辑标签、事件整编、中/英切换）
  - 新增数据库导入教程文档

### 回滚到此版本
```bash
# 如果需要回滚到此版本
git reset --hard 1d3be2d
```

### 上一个稳定版本
- **提交 hash**: `d579272`
- **提交信息**: feat: 报文融合功能完善与问题修复
- **回滚命令**: `git reset --hard d579272`
