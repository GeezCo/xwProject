# CLAUDE.md

献微系统 (Xianwei System) - 基于大模型的文章撰写与数据分析系统

## 项目概述

```
┌─────────────────┐     HTTP      ┌─────────────────┐     HTTP      ┌─────────────────┐
│   前端层        │ ───────────▶ │   后端层        │ ───────────▶ │  算法服务层     │
│  Vue 3 + Vite   │              │  Spring Boot    │              │   Flask + LLM   │
│    :8080        │              │     :8081       │              │     :5001       │
└─────────────────┘              └─────────────────┘              └─────────────────┘
                                        │                                  │
                                        ▼                                  ▼
                                 ┌─────────────┐                  ┌─────────────┐
                                 │  MySQL 8.0  │                  │  外部服务   │
                                 │   :8010     │                  │  LLM/MinIO  │
                                 └─────────────┘                  └─────────────┘
```

### 技术栈
- **前端**: Vue 3.5.13 + Vite 6.0.7 + Element Plus 2.9.1 + Pinia 2.3.0
- **后端**: Spring Boot 2.7.18 + MyBatis 2.3.2 + Druid 1.2.20
- **算法**: Python Flask 2.0+ + OpenAI SDK 1.0+
- **数据库**: MySQL 8.0.33
- **存储**: MinIO 8.5.7
- **LLM**: Qwen3.5-122B-A10B / GLM-5

---

## 快速启动

### 1. 算法服务 (必须先启动)
```bash
cd 算法/算法服务
pip install flask flask-cors openai requests
python app.py
```
端口: `5001`

### 2. 后端服务
```bash
cd 后端
mvn spring-boot:run
```
端口: `8081`

### 3. 前端服务
```bash
cd 前端/qyportrait
npm install
npm run dev
```
端口: `8080`

---

## 功能模块与接口详解

### 〇、每日要情模块（新增）

#### 功能概述
基于后台作业的事件分析系统，通过定时任务自动分析报文并提取事件信息，支持五大主题的分类查询和手动触发分析。

#### 前端组件
- `DailyBrief.vue` - 每日要情主页面（~300行）
  - 五大主题 Tab：美俄、印度、南亚、中亚、反恐
  - 日期选择器、手动触发分析、关键词配置
  - 关键词库可前端编辑（共202个关键词）
- `ThemePanel.vue` - 主题面板子组件（~150行）
  - 事件表格展示：来源报文、事件时间、事件内容、事件分析
  - 秒级加载已分析的事件数据

#### 后端接口 (EventAnalysisController)
| 路径 | 方法 | 参数 | 功能 |
|------|------|------|------|
| `/api/eventAnalysis/trigger` | POST | `{startDate, endDate}` | 手动触发分析任务（异步） |
| `/api/eventAnalysis/query` | POST | `{startDate, endDate, keywords}` | 查询事件（按关键词筛选） |
| `/api/eventAnalysis/status` | GET | date | 查询分析任务状态 |

#### 定时任务
- `DailyAnalysisTask.java` - 每日凌晨2点自动分析前一天新增报文
- `@Scheduled(cron = "0 0 2 * * ?")`

#### 数据表
- `event_analysis` - 事件分析结果表 (id, origin_text_id, event_time, event_location, event_content, event_analysis, analysis_date, create_time)
- 索引：idx_origin_text_id, idx_analysis_date
- 唯一键：uk_origin_event（避免重复分析）

#### 五大主题关键词库
- **美俄**（43个）：美国、俄罗斯、乌克兰、北约、F-16、爱国者、普京、拜登等
- **印度**（33个）：印度、莫迪、光辉战机、中印边界、QUAD、实际控制线等
- **南亚**（33个）：巴基斯坦、孟加拉国、克什米尔、中巴经济走廊、塔利班等
- **中亚**（40个）：哈萨克斯坦、上海合作组织、一带一路、中欧班列等
- **反恐**（53个）：ISIS、基地组织、塔利班、反恐行动、东伊运等

**详细文档**：参见 `每日要情功能说明.md`

---

### 一、报文管理模块

#### 前端组件
- `searchResult.vue` - 报文列表搜索、筛选、展示、详情查看（1025行，核心功能）
  - 三栏布局：分类树 | 报文列表 | 详情面板
  - 功能：搜索/筛选/分页/详情查看/属性抽取
  - 图文报支持：图片网格展示 + MinIO动态加载

#### 后端接口 (UygurController)
| 路径 | 方法 | 参数 | 功能 |
|------|------|------|------|
| `/uygur/category` | GET | 无 | 获取分类目录列表 |
| `/uygur/getTextList` | POST | `{pageNum, pageSize, typeId, modalType, keyword}` | 分页查询文本列表 |
| `/uygur/detail/{sid}` | GET | sid | 获取单个报文详情 |
| `/uygur/importFromJsonl` | POST | `file, parentCategoryName, categoryName` | 批量导入JSONL文件 |
| `/uygur/resetExtracted` | POST | 无 | 重置所有抽取状态 |
| `/uygur/config` | GET | 无 | 获取系统配置（MinIO地址等） |

#### 数据表
- `origin_text` - 原始文本表 (sid, title, content, times, type, modal_type, images, is_extracted)
- `text_type` - 分类目录表 (sid, id, type_name, parent_id)

---

### 二、属性抽取模块

#### 前端组件
- `searchResult.vue` - 点击"属性抽取"按钮触发

#### 后端接口 (ExtractionController)
| 路径 | 方法 | 参数 | 功能 |
|------|------|------|------|
| `/extraction/extract` | POST | `originTextId, force=false` | 执行LLM事件抽取 |
| `/extraction/result/{originTextId}` | GET | originTextId | 查询已保存抽取结果 |

#### 算法接口 (Flask)
| 路径 | 方法 | 参数 | 返回值 |
|------|------|------|--------|
| `/extract` | POST | `{text, origin_text_id}` | `{events[], labels[], entities{}, llm_calls, llm_calls_saved}` |
| `/extract/simple` | POST | `{text}` | `{events[], total}` |
| `/health` | GET | 无 | `{status, llm_base_url, llm_model, time}` |

#### 算法流程 (V3版本 - 直接抽取+分类)
```
输入文本 → 段落分割 → LLM直接抽取（含分类字段）
        → 后处理生成subject/labels → 输出JSON
```

**V3改进**：
- 输入预处理：防御引号问题
- Prompt优化：LLM直接输出分类字段（船只、飞机等）
- 三重JSON解析：直接解析 → Markdown代码块提取 → 花括号计数
- 状态机修复：处理不完整JSON

#### 分类标签
调用 RexUniNlu 服务对主体进行多标签分类：
- 服务地址: `http://36.103.234.242:8514`
- 预定义标签: `["人物", "组织", "部队", "军舰", "火炮", "战机", "武器", "装备"]`

#### 事件数据结构
```json
{
  "event_id": 1,
  "time": "2024年3月15日",
  "location": ["横须贺", "关岛"],
  "subject": ["萨德反导系统", "第七舰队"],
  "action": "保持战备状态",
  "labels": ["武器", "装备", "部队"],
  "船只": ["第七舰队"],
  "武器": ["萨德反导系统"],
  "original_text": "原文句子..."
}
```

#### 数据表
- `extraction_result` - 抽取结果表 (origin_text_id, events_json, labels_json, entities_json, is_extracted)

---

### 三、报文融合模块

#### 前端组件
- `fusionResult.vue` - 融合报告展示、保存、导出（541行）
  - 两栏布局：源报文列表 | 融合报告
  - 功能：多报文选择/融合生成/报告保存/导出

#### 后端接口 (FusionController)
| 路径 | 方法 | 参数 | 功能 |
|------|------|------|------|
| `/api/fusion/create` | POST | `{reports[], fusionType, customTitle}` | 创建融合报告 |
| `/api/fusion/save` | POST | FusionDTO | 保存融合报告 |
| `/api/fusion/list` | GET | `pageNum, pageSize` | 查询融合报告列表 |
| `/api/fusion/detail/{id}` | GET | id | 查询融合报告详情 |
| `/api/fusion/export/{id}` | GET | id | 导出融合报告为Markdown |
| `/api/fusion/delete/{id}` | DELETE | id | 删除融合报告 |

#### 算法接口 (Flask)
| 路径 | 方法 | 参数 | 返回值 |
|------|------|------|--------|
| `/fusion/create` | POST | `{reports: [{id, title, content, extractionResult}], fusionType, customTitle}` | `{fusionId, title, summary, timeline[], content, entities[], labels[], sourceIds[], modelUsed, createTime}` |

#### 融合流程
```
Step 1: 数据预处理 → 统一字段格式
Step 2: 信息整合（代码）→ 合并时间线、实体、标签
Step 3: LLM生成 → 标题、摘要、详细内容
Step 4: 结果组装 → 构建目标列表表格 + 输出融合报告
```

#### 融合报告结构
```json
{
  "fusionId": 1234567890,
  "title": "北约多国军事演习融合报告",
  "summary": "摘要内容...",
  "timeline": [
    {"time": "2024-03-15", "description": "事件原文（取自 event.original_text）..."}
  ],
  "content": "详细内容(Markdown格式)",
  "entities": [
    {"category": "人物", "name": "美国总统拜登", "action": "2024-03-15: 事件原文..."},
    {"category": "船只", "name": "里根号航母", "action": "2024-03-15: 事件原文..."}
  ],
  "labels": ["军事", "国际", "政治"],
  "sourceIds": [9741, 9742],
  "modelUsed": "Qwen3.5-122B-A10B",
  "createTime": "2026-04-18 12:00:00"
}
```

> **关键变更（2026-06-02）**：
> - `entities` 从分类字典 `{"人物":[],"组织":[]}` 改为目标列表表格行数组 `[{category, name, action}]`
> - `timeline[].description` 和 `entities[].action` 内容均取自 `event.original_text`（事件原文，比 action 更完整）
> - 算法层 `_build_targets_table()` 内置实体智能归一化（前后缀剥离 + 标点统一），合并同义实体

#### 数据表
- `fusion_report` - 融合报告表 (id, title, summary, timeline_json, content, entities_json, labels_json, source_ids, model_used, create_time)

---

### 四、RAG 知识库模块（已合并至 xwBackend）

#### 后端接口 (RagController + DocumentController)
| 路径 | 方法 | 功能 |
|------|------|------|
| `/api/rag/index/status` | GET | 索引状态 |
| `/api/rag/index/trigger` | POST | 触发批量索引 |
| `/api/rag/index/log` | GET | 索引日志 |
| `/api/rag/search` | POST | 混合检索（BM25 + 向量 RRF） |
| `/api/rag/document/upload` | POST | 上传 DOCX 索引 |
| `/api/rag/document/upload/mixed` | POST | 上传含图片 DOCX（含 OCR） |
| `/api/rag/document/parse` | POST | 仅解析（调试用） |
| `/api/rag/document/status/{docId}` | GET | 查询上传任务状态 |

#### 存储
- ES `xianwei_docs` 索引，dense_vector 1024 维
- MySQL `rag_document` 表记录上传元数据

---

### 五、用户认证模块

#### 前端组件
- `Login.vue` - 登录页面
- `Home.vue` - 主框架页（含导航栏、主题切换、时钟）
- 路由守卫 - token验证

#### 路由配置
| 路径 | 组件 | 功能 | keepAlive |
|------|------|------|-----------|
| `/` | Login.vue | 登录页 | 否 |
| `/Home` | Home.vue | 主框架页 | 否 |
| `/searchResult` | searchResult.vue | 报文列表页 | 是 |
| `/fusionResult` | fusionResult.vue | 融合结果页 | 否 |

---

## 算法模块详解

### 目录结构
```
算法/
├── 算法服务/app.py              # Flask REST API (端口5001)
├── 数据抽取-新/
│   ├── llm_event_extractor_v3.py  # V3事件抽取器（主入口）
│   ├── llm_event_extractor.py     # LLM事件抽取（底层）
│   ├── classify_client.py         # RexUniNlu分类客户端
│   └── config.json                # 配置文件
├── 报文融合/
│   └── fusion_extractor.py        # 报文融合算法
├── 数据生成/
│   ├── data_generator.py          # LLM数据生成
│   └── candidate_pools.py         # 候选池管理
└── 事件拆分/
    └── event_splitter.py          # 事件原子化拆分
```

### 核心算法类
| 类名 | 文件 | 功能 |
|------|------|------|
| `LLMEventExtractorV3` | llm_event_extractor_v3.py | V3事件抽取（直接抽取+分类） |
| `LLMEventExtractor` | llm_event_extractor.py | LLM精确抽取（底层） |
| `FusionExtractor` | fusion_extractor.py | 报文融合处理 |
| `DataGenerator` | data_generator.py | 基于样例生成数据 |
| `EventSplitter` | event_splitter.py | 事件原子化拆分 |

### LLM配置
```json
{
  "llm": {
    "base_url": "https://llmapi.paratera.com",
    "model": "Qwen3.5-122B-A10B",
    "api_key": "sk-Sf3cCx7aSWyk_4KUFoi8Tw",
    "temperature": 0.1,
    "max_tokens": 16000
  }
}
```

**配置说明**：
- 配置文件路径：`算法/数据抽取-新/config.json`
- 修改配置后需完全重启算法容器：`docker compose stop algorithm && docker compose rm -f algorithm && docker compose up -d algorithm`
- 查询当前配置：`curl http://localhost:5001/health`

---

## 后端模块详解

### 目录结构
```
后端/src/main/java/com/qy/dch/
├── controller/
│   ├── UygurController.java       # 报文管理接口
│   ├── ExtractionController.java  # 属性抽取接口
│   └── FusionController.java      # 报文融合接口
├── service/impl/
│   ├── UygurServiceImpl.java      # 报文管理服务
│   ├── ExtractionServiceImpl.java # 抽取服务（调用算法）
│   └── FusionServiceImpl.java     # 融合服务（调用算法）
├── mapper/
│   ├── UygurMapper.java           # 报文数据访问
│   ├── ExtractionMapper.java      # 抽取结果访问
│   └── FusionMapper.java          # 融合报告访问
├── dto/
│   ├── OriginTextDTO.java         # 原始文本DTO
│   ├── ExtractionResultDTO.java   # 抽取结果DTO
│   └── FusionDTO.java             # 融合报告DTO
├── request/
│   ├── GetListRequest.java        # 列表查询请求
│   ├── FusionCreateRequest.java   # 融合创建请求
│   └── ReportData.java            # 报文数据结构
└── config/
    ├── CorsConfig.java            # CORS跨域配置
    └── DruidConfig.java           # Druid监控配置
```

### 数据库配置
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://36.103.234.242:8010/uygur_project
    username: uygur_user
    password: uygur_2024
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
```

---

## 前端模块详解

### 目录结构
```
前端/qyportrait/src/
├── components/
│   ├── Login.vue          # 登录页
│   ├── searchResult.vue   # 报文列表页（核心功能，1025行）
│   └── fusionResult.vue   # 融合结果页（541行）
├── views/
│   └── Home.vue           # 主框架页
├── stores/
│   └── home.js            # Pinia状态管理
├── router/
│   └── index.js           # 路由配置
├── utils/
│   ├── request.js         # Axios封装
│   └── auth.js            # Token管理
└── assets/                # 静态资源
```

### 关键API调用
| 接口 | 调用位置 | 功能 |
|------|----------|------|
| `/uygur/getTextList` | searchResult.vue | 获取报文列表 |
| `/uygur/config` | stores/home.js | 获取MinIO配置 |
| `/extraction/extract` | searchResult.vue | 执行属性抽取 |
| `/extraction/result/{id}` | searchResult.vue/fusionResult.vue | 获取抽取结果 |
| `/api/fusion/create` | fusionResult.vue | 创建融合报告 |
| `/api/fusion/save` | fusionResult.vue | 保存融合报告 |

### 状态管理 (Pinia)
```javascript
// stores/home.js
export const useHomeStore = defineStore('home', () => {
  const token = ref(getToken() || '')
  const theme = ref(localStorage.getItem('theme') || 'dark')
  const minioPrefix = ref('')  // 动态获取MinIO地址
  const fusionData = ref(null)
  
  async function fetchConfig() {
    const res = await request.get('/uygur/config')
    if (res.data?.minioPrefix) {
      minioPrefix.value = res.data.minioPrefix
    }
  }
  
  return { token, theme, minioPrefix, fusionData, fetchConfig }
})
```

---

## 性能优化

1. **V3抽取器优化**: 
   - 输入预处理防御引号问题
   - 三重JSON解析策略提高成功率
   - 状态机修复处理不完整JSON

2. **长超时设置**: 后端设置20分钟超时适配LLM处理

3. **缓存策略**: 
   - 已抽取结果缓存于数据库，避免重复处理
   - 前端keepAlive缓存searchResult页面

4. **多编码支持**: JSONL导入支持UTF-8、GBK、GB2312自动识别

5. **连接池优化**: Druid连接池配置（初始5，最大20）

---

## 部署说明

### 开发环境
```bash
# 1. 启动算法服务
cd 算法/算法服务
python app.py

# 2. 启动后端
cd 后端
mvn spring-boot:run

# 3. 启动前端
cd 前端/qyportrait
npm run dev
```

### 生产环境（Docker）
```bash
# 使用 deploy/ 目录下的部署脚本
cd deploy
bash setup.sh
```

**容器列表**：
- `xianwei-frontend` - Nginx 前端（端口 9201）
- `xianwei-backend` - Spring Boot 后端（端口 9202）
- `xianwei-algorithm` - Flask 算法服务（端口 9203）
- `xianwei-mysql` - MySQL 数据库（端口 9204）
- `xianwei-minio` - MinIO 对象存储（API 9205，Console 9206）

---

## 注意事项

1. **启动顺序**: 算法服务 → 后端 → 前端
2. **抽取耗时**: 单篇报文抽取耗时 2-5 分钟
3. **GLM-5特殊处理**: 返回内容在 `reasoning_content` 字段
4. **配置修改**: 修改 LLM 配置后需完全重启算法容器
5. **MinIO配置**: 前端动态获取MinIO地址，修改后端 `application-prod.yml` 即可
6. **数据库导入**: 参考 `deploy/数据库导入教程.md`
7. **离线部署**: 使用 `xianwei-algorithm:offline` 镜像（75MB）

---

## 相关文档

- [系统框架说明.md](系统框架说明.md) - 详细架构设计
- [deploy/数据库导入教程.md](deploy/数据库导入教程.md) - 数据导入方法
- [deploy/迁移部署指南.md](deploy/迁移部署指南.md) - 生产环境部署
- [Git备份与回滚指南.md](Git备份与回滚指南.md) - Git操作说明
- [编程规范.md](编程规范.md) - 代码规范
