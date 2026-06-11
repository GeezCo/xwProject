#!/bin/bash

# ============================================
# 献微系统 - origin_text 表结构调整一键执行脚本
# 日期: 2026-06-09
# 用途: 在176服务器上执行数据库表结构调整
# ============================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
SERVER_HOST="36.141.21.176"
SERVER_PORT="1111"
SERVER_USER="yinbanghu"
SSH_KEY="~/.ssh/id_rsa_ascend910b"
CONTAINER_NAME="xianwei-mysql"
DB_NAME="uygur_project"
DB_USER="root"
DB_PASSWORD="jixianyuan1314"
SQL_FILE="alter_origin_text.sql"
LOCAL_SQL_PATH="./deploy/${SQL_FILE}"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}献微系统 - 数据库表结构调整${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 步骤1: 检查SQL文件是否存在
echo -e "${YELLOW}[1/5] 检查SQL文件...${NC}"
if [ ! -f "${LOCAL_SQL_PATH}" ]; then
    echo -e "${RED}错误: SQL文件不存在 ${LOCAL_SQL_PATH}${NC}"
    exit 1
fi
echo -e "${GREEN}✓ SQL文件存在${NC}"
echo ""

# 步骤2: 上传SQL文件到服务器
echo -e "${YELLOW}[2/5] 上传SQL文件到176服务器...${NC}"
scp -i ${SSH_KEY} -P ${SERVER_PORT} "${LOCAL_SQL_PATH}" ${SERVER_USER}@${SERVER_HOST}:/tmp/
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 文件上传成功${NC}"
else
    echo -e "${RED}✗ 文件上传失败${NC}"
    exit 1
fi
echo ""

# 步骤3: 复制文件到MySQL容器
echo -e "${YELLOW}[3/5] 复制文件到MySQL容器...${NC}"
ssh -i ${SSH_KEY} -p ${SERVER_PORT} ${SERVER_USER}@${SERVER_HOST} \
    "docker cp /tmp/${SQL_FILE} ${CONTAINER_NAME}:/tmp/"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 文件复制到容器成功${NC}"
else
    echo -e "${RED}✗ 文件复制失败${NC}"
    exit 1
fi
echo ""

# 步骤4: 执行SQL脚本
echo -e "${YELLOW}[4/5] 执行SQL脚本...${NC}"
echo -e "${YELLOW}提示: 这可能需要几分钟，请耐心等待...${NC}"
ssh -i ${SSH_KEY} -p ${SERVER_PORT} ${SERVER_USER}@${SERVER_HOST} \
    "docker exec -i ${CONTAINER_NAME} mysql -u ${DB_USER} -p'${DB_PASSWORD}' ${DB_NAME} < /tmp/${SQL_FILE}"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ SQL脚本执行成功${NC}"
else
    echo -e "${RED}✗ SQL脚本执行失败${NC}"
    echo -e "${YELLOW}提示: 请检查上面的错误信息${NC}"
    exit 1
fi
echo ""

# 步骤5: 验证结果
echo -e "${YELLOW}[5/5] 验证表结构...${NC}"
ssh -i ${SSH_KEY} -p ${SERVER_PORT} ${SERVER_USER}@${SERVER_HOST} \
    "docker exec ${CONTAINER_NAME} mysql -u ${DB_USER} -p'${DB_PASSWORD}' ${DB_NAME} --default-character-set=utf8mb4 -e 'DESCRIBE origin_text;'" 2>&1 | grep -v "Warning"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ 表结构调整完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "变更内容："
echo "  1. times 字段: VARCHAR(255) → DATETIME"
echo "  2. 新增字段: briefTypeName (VARCHAR(100))"
echo "  3. 新增字段: sendUnitName (VARCHAR(100))"
echo "  4. 新增索引: idx_times, idx_briefTypeName, idx_sendUnitName"
echo ""
