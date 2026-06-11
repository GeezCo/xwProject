# MySQL数据库登录信息

## 数据库配置

| 参数 | 值 |
|------|------|
| **主机** | `36.103.234.242` |
| **端口** | `8010` |
| **数据库** | `uygur_project` |
| **用户** | `root` |
| **密码** | `jixianyuan1314` |

## 登录方式

### 方式一：本地客户端连接

```bash
mysql -h 36.103.234.242 -P 8010 -u root -pjixianyuan1314 uygur_project
```

### 方式二：SSH登录服务器后本地连接

```bash
# SSH登录服务器
ssh -i "服务器配置/mecs-bd71de15.id" -p 2327 ubuntu@36.103.234.242

# 进入root
sudo su -

# 本地连接MySQL（服务器内部网络更快）
mysql -h 127.0.0.1 -P 8010 -u root -pjixianyuan1314 uygur_project
```

### 方式三：通过Docker容器连接

```bash
# SSH登录后查看MySQL容器
docker ps | grep mysql

# MySQL容器信息：
# - qy-mysql (MySQL 5.7): 端口 8010
# - MYSQL_ROOT_PASSWORD: qiyuan123（容器内部密码，但实际连接用上面的密码）

# 进入容器执行SQL
docker exec -i qy-mysql mysql -u root -pjixianyuan1314 uygur_project -e "SQL语句"
```

## 常用操作

### 查看数据库列表
```sql
SHOW DATABASES;
```

### 查看表结构
```sql
SHOW TABLES;
DESC origin_text;
```

### 重置抽取状态
```sql
-- 重置所有报文为未抽取
UPDATE origin_text SET is_extracted = 0;

-- 查看抽取状态统计
SELECT 
    COUNT(*) as total,
    SUM(CASE WHEN is_extracted=1 THEN 1 ELSE 0 END) as extracted,
    SUM(CASE WHEN is_extracted=0 THEN 1 ELSE 0 END) as not_extracted
FROM origin_text;
```

## 服务器信息

| 参数 | 值 |
|------|------|
| **服务器IP** | `36.103.234.242` |
| **SSH端口** | `2327` |
| **SSH用户** | `ubuntu` |
| **SSH密钥** | `服务器配置/mecs-bd71de15.id` |
| **进入root** | `sudo su -` |

## 注意事项

- 密码在命令行中会有警告提示，不影响执行
- 数据库连接需确保IP在白名单中
- 后端配置文件位置：`后端/src/main/resources/application.yml`