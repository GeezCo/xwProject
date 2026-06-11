#!/bin/bash
# 每日数据生成定时任务脚本
# 路径: /ssd/liuyukun/DataGenerator/daily_generate.sh

# 设置工作目录
WORK_DIR="/ssd/liuyukun/DataGenerator"
cd $WORK_DIR

# 获取当前日期
CURRENT_DATE=$(date +%Y-%m-%d)
CURRENT_DATE_SHORT=$(date +%Y%m%d)

# 定义输出目录
OUTPUT_DIR="输出_${CURRENT_DATE_SHORT}"

# 日志文件
LOG_FILE="${WORK_DIR}/logs/daily_${CURRENT_DATE_SHORT}.log"

# 创建日志目录
mkdir -p "${WORK_DIR}/logs"

# 记录开始时间
echo "========================================" | tee -a $LOG_FILE
echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a $LOG_FILE
echo "生成日期: ${CURRENT_DATE}" | tee -a $LOG_FILE
echo "输出目录: ${OUTPUT_DIR}" | tee -a $LOG_FILE
echo "========================================" | tee -a $LOG_FILE

# 执行数据生成任务（使用jsonl格式，指定当天日期）
python3 data_generator.py 样例 "${OUTPUT_DIR}" \
    --start_date ${CURRENT_DATE} \
    --end_date ${CURRENT_DATE} \
    --item_diversity low \
    --content_diversity high \
    --total 10 \
    --parallel 1 \
    --model GLM-5 \
    --output_format jsonl 2>&1 | tee -a $LOG_FILE

# 检查生成结果
JSONL_FILE="${OUTPUT_DIR}/data_${CURRENT_DATE_SHORT}.jsonl"
if [ -f "${JSONL_FILE}" ]; then
    LINE_COUNT=$(wc -l < "${JSONL_FILE}")
    echo "生成完成: ${JSONL_FILE} 共 ${LINE_COUNT} 条数据" | tee -a $LOG_FILE
else
    echo "警告: jsonl文件不存在: ${JSONL_FILE}" | tee -a $LOG_FILE
    # 检查输出目录是否有其他文件
    if [ -d "${OUTPUT_DIR}" ]; then
        echo "输出目录内容:" | tee -a $LOG_FILE
        ls -la "${OUTPUT_DIR}/" | tee -a $LOG_FILE
    else
        echo "错误: 输出目录不存在，生成任务可能失败" | tee -a $LOG_FILE
        exit 1
    fi
fi

# 记录结束时间
echo "========================================" | tee -a $LOG_FILE
echo "结束时间: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a $LOG_FILE
echo "========================================" | tee -a $LOG_FILE