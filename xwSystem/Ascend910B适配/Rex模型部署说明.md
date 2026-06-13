# iic/nlp_deberta_rex-uninlu_chinese-base 模型部署说明

## 模型简介

- **模型ID**: `iic/nlp_deberta_rex-uninlu_chinese-base`
- **功能**: 通用信息抽取（Universal Information Extraction）
- **支持任务**:
  - 命名实体识别（NER）
  - 关系抽取
  - 事件抽取
  - 属性情感抽取
  - 文本分类
  - 零样本分类
  - 自然语言推理

---

## 部署环境

| 项目 | 值 |
|------|-----|
| 服务器IP | 36.141.21.176 |
| SSH端口 | 1111 |
| 用户 | yinbanghu |
| 架构 | ARM64 (aarch64) |
| NPU | Ascend 910B2 (8卡) |

---

## 部署步骤

### 1. 登录服务器

```bash
ssh -i ~/.ssh/id_rsa_ascend910b -p 1111 yinbanghu@36.141.21.176
```

### 2. 安装依赖并下载模型

```bash
# 安装modelscope
pip3 install modelscope transformers

# 下载模型
mkdir -p ~/models
~/.local/bin/ms download iic/nlp_deberta_rex-uninlu_chinese-base --local_dir ~/models/nlp_deberta_rex-uninlu_chinese-base
```

### 3. 创建Docker容器

```bash
docker run -d --name rex-model \
  --network host \
  -v /usr/local/dcmi:/usr/local/dcmi \
  -v /usr/local/bin/npu-smi:/usr/local/bin/npu-smi \
  -v /usr/local/Ascend/driver/lib64:/usr/local/Ascend/driver/lib64 \
  -v /usr/local/Ascend/driver/version.info:/usr/local/Ascend/driver/version.info \
  -v /etc/ascend_install.info:/etc/ascend_install.info \
  -v ~/models:/models \
  quay.io/ascend/vllm-ascend:releases-v0.13.0 \
  /bin/bash -c 'tail -f /dev/null'
```

### 4. 安装容器内依赖

```bash
docker exec rex-model pip install \
  transformers==4.48.3 \
  addict \
  jsonlines \
  datasets \
  simplejson \
  sortedcontainers \
  accelerate \
  -q
```

---

## 使用方法

### 基本用法

```python
import sys
sys.path.insert(0, '/models/nlp_deberta_rex-uninlu_chinese-base')
from modelscope.pipelines import pipeline

# 加载模型
inference = pipeline('rex-uninlu', model='/models/nlp_deberta_rex-uninlu_chinese-base')

# 定义schema
text = '1944年毕业于北大的名古屋铁道会长谷口清太郎等人在日本积极筹资。'
schema = {'人物': None, '地理位置': None, '组织机构': None}

# 执行抽取
output = inference(text, schema=schema)
print(output)
```

### Schema定义示例

1. **实体抽取**:
```python
schema = {'人物': None, '地理位置': None, '组织机构': None}
```

2. **属性情感抽取**:
```python
schema = {'属性词': {'情感词': None}}
```

3. **关系抽取**:
```python
schema = {'组织机构': {'创始人': None, '成立日期': None}}
```

---

## 测试命令

在容器内执行：

```bash
docker exec rex-model python3 -c "
import sys
sys.path.insert(0, '/models/nlp_deberta_rex-uninlu_chinese-base')
from modelscope.pipelines import pipeline

inference = pipeline('rex-uninlu', model='/models/nlp_deberta_rex-uninlu_chinese-base')

text = '谷口清太郎毕业于北大'
schema = {'人物': None}
output = inference(text, schema=schema)
print('Result:', output['output'])
"
```

---

## 管理命令

### 查看容器状态
```bash
docker ps | grep rex-model
```

### 进入容器
```bash
docker exec -it rex-model bash
```

### 停止容器
```bash
docker stop rex-model
```

### 启动容器
```bash
docker start rex-model
```

### 删除容器
```bash
docker rm -f rex-model
```

---

## 注意事项

1. **transformers版本**: 需要使用4.48.3版本，更高版本可能不兼容
2. **CPU模式**: 当前模型运行在CPU模式，未使用NPU加速
3. **依赖完整**: 确保安装所有依赖包（addict, jsonlines, datasets等）
4. **Schema定义**: 根据抽取任务类型定义合适的schema结构