4 CSCI体系结构设计

4.1 CSCI部件

　　献微系统CSCI由8个软件单元组成，各软件单元之间的静态关系如下图所示：

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                          献微系统 CSCI（XW-CSCI-001）                             │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌─────────────────┐  触发  ┌─────────────────┐  调用  ┌─────────────────┐      │
│  │  SU-001         │───────▶│  SU-002         │───────▶│  SU-008         │      │
│  │  报文管理模块   │        │  属性抽取模块   │        │  算法服务模块   │      │
│  └─────────────────┘        └─────────────────┘        └─────────────────┘      │
│         │                            │                          ▲                │
│         │ 提供报文                   │ 提供抽取结果             │                │
│         ▼                            ▼                          │ 调用           │
│  ┌─────────────────┐         ┌─────────────────┐               │                │
│  │  SU-003         │ 依赖    │  SU-004         │───────────────┘                │
│  │  报文融合模块   │────────▶│  情报分析模块   │                                │
│  └─────────────────┘         └─────────────────┘                                │
│                                                                                  │
│  ┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐   │
│  │  SU-005         │ 依赖    │  SU-006         │  推送   │  WebSocket      │   │
│  │  轨迹分析模块   │────────▶│  告警管理模块   │────────▶│  连接           │   │
│  └─────────────────┘         └─────────────────┘         └─────────────────┘   │
│         │                                                                        │
│         │ 调用算法                                                               │
│         ▼                                                                        │
│  ┌─────────────────┐                                                            │
│  │  SU-007         │                                                            │
│  │  侦察筹划模块   │                                                            │
│  └─────────────────┘                                                            │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

图4-1 献微系统软件单元静态关系图

　　各软件单元的构成和用途说明如下：

表4-1 CSCI软件单元清单表

| 软件单元标识 | 软件单元名称 | 开发状态 | 程序库位置 |
|--------------|--------------|----------|------------|
| SU-001 | 报文管理模块 | 已实现 | 后端/src/main/java/com/qy/dch/controller/UygurController.java |
| SU-002 | 属性抽取模块 | 已实现 | 后端/src/main/java/com/qy/dch/controller/ExtractionController.java |
| SU-003 | 报文融合模块 | 已实现 | 后端/src/main/java/com/qy/dch/controller/FusionController.java |
| SU-004 | 情报分析模块 | 规划中 | 后端/src/main/java/com/qy/dch/controller/IntelligenceController.java |
| SU-005 | 轨迹分析模块 | 规划中 | 后端/src/main/java/com/qy/dch/controller/TrackController.java |
| SU-006 | 告警管理模块 | 规划中 | 后端/src/main/java/com/qy/dch/controller/AlertController.java |
| SU-007 | 侦察筹划模块 | 规划中 | 后端/src/main/java/com/qy/dch/controller/ReconController.java |
| SU-008 | 算法服务模块 | 部分实现 | 算法/算法服务/app.py |

a) SU-001 报文管理模块：负责报文的增删改查、分类目录管理、分页查询和批量导入功能。实现了14个REST接口端点，通过MyBatis访问origin_text和text_type数据表。分配的需求包括REQ-TEXT-001至REQ-TEXT-005。

b) SU-002 属性抽取模块：负责接收前端的属性抽取请求，调用算法服务执行事件抽取，并将抽取结果持久化到数据库。实现了2个REST接口端点，通过RestTemplate调用算法服务的/extract接口。分配的需求包括REQ-EXT-001至REQ-EXT-002。

c) SU-003 报文融合模块：负责接收前端的融合请求，准备源报文数据及其抽取结果，调用算法服务生成融合报告，支持报告的保存、列表查询和导出功能。实现了6个REST接口端点。分配的需求包括REQ-FUS-001至REQ-FUS-004。

d) SU-004 情报分析模块：负责知识图谱构建、实体关系查询、事件时间轴生成和区域热力图展示。规划实现8个REST接口端点，通过算法服务调用LLM进行实体消歧和关系推断。分配的需求包括REQ-INTEL-001至REQ-INTEL-008。

e) SU-005 轨迹分析模块：负责目标轨迹查询、历史回放、预测分析、规律分析和异常检测。规划实现13个REST接口端点和1个WebSocket端点，通过算法服务调用轨迹预测和聚类分析算法。分配的需求包括REQ-TRACK-001至REQ-TRACK-013。

f) SU-006 告警管理模块：负责告警规则配置、实时告警推送和历史查询功能。规划实现10个REST接口端点和1个WebSocket端点，支持区域告警、速度告警、集结告警等多种告警类型。分配的需求包括REQ-ALERT-001至REQ-ALERT-010。

g) SU-007 侦察筹划模块：负责态势总览、方案编制、资源调度、方案评估和审批流转功能。规划实现21个REST接口端点和1个WebSocket端点，通过算法服务调用LLM进行情报空白识别、方案生成和可行性评估。分配的需求包括REQ-RECON-001至REQ-RECON-021。

h) SU-008 算法服务模块：提供基于大语言模型的事件抽取、报文融合、实体消歧、关系推断、轨迹预测、异常检测、方案生成和方案评估等核心算法。采用Python Flask框架实现REST API，已实现4个端点（/extract、/extract/simple、/fusion/create、/health），规划新增12个端点。分配的需求包括REQ-ALG-001至REQ-ALG-014。

4.2 执行方案

4.2.1 启动执行顺序

　　系统各软件单元的启动顺序如下：

a) 第一步：启动算法服务模块（SU-004），Flask应用监听5001端口。算法服务必须先于后端启动，因为后端启动后可能立即接收到抽取或融合请求。
b) 第二步：启动后端服务（SU-001至SU-003），Spring Boot应用监听8081端口。后端启动时初始化数据库连接池、RestTemplate和MinIO客户端。
c) 第三步：启动前端服务，Vite开发服务器监听8080端口（或生产环境由Nginx提供静态文件服务）。

4.2.2 数据流

　　系统运行期间的数据流如下：

```
┌──────┐  HTTP请求  ┌──────────┐  HTTP请求  ┌──────────┐  OpenAI SDK  ┌──────────┐
│ 前端 │──────────▶│   后端   │──────────▶│ 算法服务 │──────────▶│ LLM API  │
│ Vue3 │           │ Spring   │           │  Flask   │            │ Paratera │
│      │◀──────────│ Boot     │◀──────────│          │◀──────────│          │
└──────┘  JSON响应  └──────────┘  JSON响应  └──────────┘  JSON响应  └──────────┘
                        │  ▲                     │
                   JDBC │  │ ResultSet      HTTP │
                        ▼  │                     ▼
                    ┌──────────┐           ┌──────────┐
                    │  MySQL   │           │ RexUniNlu│
                    │  8.0     │           │ 分类服务  │
                    └──────────┘           └──────────┘
```

图4-2 系统数据流示意图

a) 报文查询数据流：前端发送POST /uygur/getTextList请求 → 后端解析参数 → MyBatis执行SQL查询 → 返回分页结果JSON
b) 属性抽取数据流：前端发送POST /extraction/extract请求 → 后端查询报文内容 → RestTemplate调用算法服务POST /extract → 算法服务调用LLM API → 抽取结果返回 → 后端持久化到extraction_result表 → 返回结果JSON
c) 报文融合数据流：前端发送POST /api/fusion/create请求 → 后端准备源报文及抽取结果 → RestTemplate调用算法服务POST /fusion/create → 算法服务整合信息并调用LLM生成报告 → 融合报告返回

4.2.3 异常处理

a) LLM API调用失败：算法服务采用最多3次重试机制，每次间隔2秒。3次重试均失败后返回错误响应
b) 数据库连接异常：Druid连接池配置最大等待时间60秒，超时后抛出异常，后端返回错误响应
c) 算法服务不可用：后端RestTemplate设置20分钟超时，超时后返回错误响应提示算法服务不可达
d) JSON解析失败：算法服务采用三重解析策略（SD-004），所有策略均失败时返回解析错误

4.2.4 并发执行

a) 后端Spring Boot应用支持多线程并发处理请求，Tomcat默认最大线程数200
b) 数据库连接池（Druid）配置最大活跃连接20，最小空闲连接5
c) 多个抽取请求可并发执行，每个请求独立调用算法服务
d) extraction_result表通过origin_text_id唯一索引保证同一报文不会产生重复抽取结果

4.3 接口设计

4.3.1 接口标识和图表

　　本CSCI包含以下9个外部和内部接口：

表4-2 接口清单表

| 接口标识 | 接口名称 | 接口类型 | 通信协议 | 接口实体 |
|----------|----------|----------|----------|----------|
| INT-001 | 前端-后端HTTP接口 | 外部 | HTTP REST | Vue 3前端 ↔ Spring Boot后端 |
| INT-002 | 后端-算法服务HTTP接口 | 内部 | HTTP REST | Spring Boot后端 → Flask算法服务 |
| INT-003 | 后端-MySQL接口 | 内部 | JDBC | Spring Boot后端 → MySQL 8.0 |
| INT-004 | 后端-MinIO接口 | 内部 | HTTP | Spring Boot后端 → MinIO对象存储 |
| INT-005 | 算法-LLM API接口 | 外部 | HTTPS | Flask算法服务 → Paratera LLM平台 |
| INT-006 | 算法-分类服务接口 | 外部 | HTTP | Flask算法服务 → RexUniNlu服务 |
| INT-007 | 前端-后端WebSocket接口 | 外部 | WebSocket | Vue 3前端 ↔ Spring Boot后端 |
| INT-008 | 后端-外部天气服务接口 | 外部 | HTTP | Spring Boot后端 → 天气数据服务 |
| INT-009 | 后端-外部地图服务接口 | 外部 | HTTP | Spring Boot后端 → 地图瓦片服务 |

```
┌──────────┐                ┌───────────────┐                ┌───────────────┐
│  前端    │── INT-001 ───▶│    后端       │── INT-002 ───▶│  算法服务     │
│  Vue 3   │               │  Spring Boot  │               │   Flask       │
│  :8080   │◀─ INT-001 ───│    :8081      │◀─ INT-002 ───│   :5001       │
│          │               │               │               │               │
│          │◀─ INT-007 ───│  (WebSocket)  │               │               │
└──────────┘               └───────────────┘               └───────────────┘
                                │   │   │                    │        │
                           INT-003 │ INT-004              INT-005  INT-006
                                │ INT-008                    │        │
                                │ INT-009                    │        │
                                ▼   ▼   ▼                    ▼        ▼
                           ┌────────┐ ┌──────┐         ┌────────┐ ┌────────┐
                           │ MySQL  │ │MinIO │         │LLM API │ │分类服务│
                           │ :8512  │ │:8522 │         │Paratera│ │:8514   │
                           └────────┘ └──────┘         └────────┘ └────────┘
                                      ┌──────┐
                                      │外部  │
                                      │服务  │
                                      └──────┘
```

图4-3 CSCI接口拓扑图

4.3.2 INT-001 前端-后端HTTP接口

a) 接口实体：Vue 3前端应用、Spring Boot后端应用
b) 通信协议：HTTP/1.1，REST风格
c) 数据格式：请求和响应均为JSON格式（Content-Type: application/json）
d) 认证方式：请求头携带token字段
e) 超时设置：20分钟（1200000ms）
f) 跨域配置：允许来源http://localhost:8080，允许方法GET/POST/PUT/DELETE

　　主要接口端点如下：

表4-3 INT-001接口端点清单表（已实现22个，规划新增53个）

| 端点路径 | 方法 | 功能说明 | 所属模块 | 状态 |
|----------|------|----------|----------|------|
| /uygur/config | GET | 获取MinIO配置 | SU-001 | 已实现 |
| /uygur/category | GET | 获取分类目录树 | SU-001 | 已实现 |
| /uygur/getTextList | POST | 分页查询报文列表 | SU-001 | 已实现 |
| /uygur/detail/{sid} | GET | 获取报文详情 | SU-001 | 已实现 |
| /uygur/addCategory | POST | 新增分类 | SU-001 | 已实现 |
| /uygur/importFromJsonl | POST | JSONL批量导入 | SU-001 | 已实现 |
| /uygur/resetExtracted | POST | 重置全部抽取状态 | SU-001 | 已实现 |
| /uygur/category/{id} | DELETE | 删除分类 | SU-001 | 已实现 |
| /uygur/category/batchDelete | POST | 批量删除分类 | SU-001 | 已实现 |
| /uygur/text/{sid} | DELETE | 删除报文 | SU-001 | 已实现 |
| /uygur/text/batchDelete | POST | 批量删除报文 | SU-001 | 已实现 |
| /uygur/category/{id}/withTexts | DELETE | 删除分类及其报文 | SU-001 | 已实现 |
| /uygur/text/updateByOldType | POST | 按旧分类批量更新 | SU-001 | 已实现 |
| /uygur/savetext | GET | 批量导入文本数据 | SU-001 | 已实现 |
| /extraction/extract | POST | 执行属性抽取 | SU-002 | 已实现 |
| /extraction/result/{originTextId} | GET | 查询抽取结果 | SU-002 | 已实现 |
| /api/fusion/create | POST | 创建融合报告 | SU-003 | 已实现 |
| /api/fusion/save | POST | 保存融合报告 | SU-003 | 已实现 |
| /api/fusion/list | GET | 融合报告列表 | SU-003 | 已实现 |
| /api/fusion/detail/{id} | GET | 融合报告详情 | SU-003 | 已实现 |
| /api/fusion/delete/{id} | DELETE | 删除融合报告 | SU-003 | 已实现 |
| /api/fusion/export/{id} | GET | 导出融合报告为Markdown | SU-003 | 已实现 |
| /api/statistics/overview | GET | 核心指标概览 | SU-001 | 规划中 |
| /api/statistics/categoryDistribution | GET | 报文分类分布 | SU-001 | 规划中 |
| /api/statistics/modalDistribution | GET | 报文模态分布 | SU-001 | 规划中 |
| /api/statistics/timeDistribution | GET | 报文时间分布 | SU-001 | 规划中 |
| /api/statistics/extractionProgress | GET | 抽取进度 | SU-001 | 规划中 |
| /api/statistics/topLabels | GET | 高频标签TOP N | SU-001 | 规划中 |
| /api/statistics/entityDistribution | GET | 实体类型分布 | SU-001 | 规划中 |
| /api/statistics/recentFusions | GET | 最近融合报告 | SU-001 | 规划中 |
| /api/intelligence/graph | GET | 获取知识图谱数据 | SU-004 | 规划中 |
| /api/intelligence/entity/{id} | GET | 实体详情+关联 | SU-004 | 规划中 |
| /api/intelligence/graph/build | POST | 触发图谱构建/更新 | SU-004 | 规划中 |
| /api/intelligence/timeline | GET | 事件时间轴 | SU-004 | 规划中 |
| /api/intelligence/heatmap | GET | 区域热力图数据 | SU-004 | 规划中 |
| /api/intelligence/region/{regionId} | GET | 区域详情 | SU-004 | 规划中 |
| /api/intelligence/search | GET | 实体/事件搜索 | SU-004 | 规划中 |
| /api/intelligence/export | GET | 导出图谱数据 | SU-004 | 规划中 |
| /api/track/targets | GET | 目标列表 | SU-005 | 规划中 |
| /api/track/target/{id} | GET | 目标详情 | SU-005 | 规划中 |
| /api/track/history/{targetId} | GET | 目标历史轨迹 | SU-005 | 规划中 |
| /api/track/predict/{targetId} | GET | 目标预测路线 | SU-005 | 规划中 |
| /api/track/playback/{targetId} | GET | 回放数据加载 | SU-005 | 规划中 |
| /api/track/playback/overlay | GET | 多目标叠加轨迹 | SU-005 | 规划中 |
| /api/track/playback/{targetId}/stats | GET | 行军统计 | SU-005 | 规划中 |
| /api/track/pattern/areas/{targetId} | GET | 集结/驻留区域分析 | SU-005 | 规划中 |
| /api/track/pattern/time/{targetId} | GET | 时间规律分析 | SU-005 | 规划中 |
| /api/track/pattern/routes/{targetId} | GET | 行军路线分析 | SU-005 | 规划中 |
| /api/track/pattern/anomalies/{targetId} | GET | 异常行为检测 | SU-005 | 规划中 |
| /api/track/pattern/similar/{targetId} | GET | 相似目标匹配 | SU-005 | 规划中 |
| /api/alert/rules | GET | 告警规则列表 | SU-006 | 规划中 |
| /api/alert/rules | POST | 创建告警规则 | SU-006 | 规划中 |
| /api/alert/rules/{id} | PUT | 更新告警规则 | SU-006 | 规划中 |
| /api/alert/rules/{id} | DELETE | 删除告警规则 | SU-006 | 规划中 |
| /api/alert/rules/{id}/toggle | PUT | 启用/禁用规则 | SU-006 | 规划中 |
| /api/alert/realtime | GET | 实时告警列表 | SU-006 | 规划中 |
| /api/alert/{id}/handle | PUT | 处理告警 | SU-006 | 规划中 |
| /api/alert/history | GET | 告警历史 | SU-006 | 规划中 |
| /api/alert/statistics | GET | 告警统计 | SU-006 | 规划中 |
| /api/recon/overview | GET | 态势数据 | SU-007 | 规划中 |
| /api/recon/gaps | GET | 情报空白列表 | SU-007 | 规划中 |
| /api/recon/gaps/identify | POST | AI识别情报空白 | SU-007 | 规划中 |
| /api/recon/plans | POST | 创建侦察方案 | SU-007 | 规划中 |
| /api/recon/plans/{id} | PUT | 更新方案 | SU-007 | 规划中 |
| /api/recon/plans/{id} | GET | 方案详情 | SU-007 | 规划中 |
| /api/recon/plans | GET | 方案列表 | SU-007 | 规划中 |
| /api/recon/plans/{id}/generate | POST | AI辅助生成方案 | SU-007 | 规划中 |
| /api/recon/plans/{id}/constraints | POST | 约束分析 | SU-007 | 规划中 |
| /api/recon/assets | GET | 资源列表 | SU-007 | 规划中 |
| /api/recon/assets/{id} | PUT | 更新资源状态 | SU-007 | 规划中 |
| /api/recon/schedule | GET | 调度日历 | SU-007 | 规划中 |
| /api/recon/schedule | POST | 创建调度任务 | SU-007 | 规划中 |
| /api/recon/schedule/{id} | PUT | 更新调度任务 | SU-007 | 规划中 |
| /api/recon/plans/{id}/evaluation | GET | 方案评估结果 | SU-007 | 规划中 |
| /api/recon/plans/{id}/evaluate | POST | 触发AI评估 | SU-007 | 规划中 |
| /api/recon/plans/{id}/approval | GET | 审批流转状态 | SU-007 | 规划中 |
| /api/recon/plans/{id}/approve | POST | 审批操作 | SU-007 | 规划中 |
| /api/recon/plans/{id}/results | GET | 成果汇总 | SU-007 | 规划中 |
| /api/recon/plans/{id}/results | POST | 提交侦察成果 | SU-007 | 规划中 |

4.3.3 INT-002 后端-算法服务HTTP接口

a) 接口实体：Spring Boot后端应用、Flask算法服务
b) 通信协议：HTTP/1.1
c) 数据格式：JSON
d) 调用方式：后端通过RestTemplate发起同步HTTP请求
e) 超时设置：连接超时和读取超时均为20分钟

表4-4 INT-002接口端点清单表（已实现4个，规划新增12个）

| 端点路径 | 方法 | 功能说明 | 输入参数 | 输出参数 | 状态 |
|----------|------|----------|----------|----------|------|
| /health | GET | 健康检查 | 无 | status, service, time | 已实现 |
| /extract | POST | 层次化事件抽取 | text, origin_text_id | events[], labels[], llm_calls | 已实现 |
| /extract/simple | POST | 简单抽取 | text | events[], total | 已实现 |
| /fusion/create | POST | 报文融合 | reports[], fusionType | title, summary, timeline[], content | 已实现 |
| /intelligence/build_graph | POST | 构建实体关系图谱 | reports[], extractionResults[] | nodes[], edges[] | 规划中 |
| /intelligence/build_timeline | POST | 构建事件时间轴 | events[], timeRange | timeline[], causalRelations[] | 规划中 |
| /track/predict | POST | 轨迹预测 | targetId, historyPoints[], duration | predictedPoints[], confidence | 规划中 |
| /track/pattern/areas | POST | 集结区域识别 | targetId, historyPoints[] | clusters[], centers[] | 规划中 |
| /track/pattern/time | POST | 时间规律分析 | targetId, historyPoints[] | heatmapMatrix[][] | 规划中 |
| /track/pattern/routes | POST | 常用路线分析 | targetId, historyPoints[] | routeClusters[], frequency[] | 规划中 |
| /track/pattern/anomalies | POST | 异常行为检测 | targetId, recentPoints[], baseline | anomalies[], scores[] | 规划中 |
| /track/pattern/similar | POST | 相似目标匹配 | targetId, features{} | similarTargets[], similarity[] | 规划中 |
| /recon/identify_gaps | POST | AI识别情报空白 | theater, existingIntel[] | gaps[], priority[] | 规划中 |
| /recon/generate_plan | POST | AI辅助生成方案 | taskDef{}, resources[], constraints{} | planContent{} | 规划中 |
| /recon/evaluate_plan | POST | AI评估方案 | planContent{} | scores{}, suggestions[] | 规划中 |
| /recon/analyze_constraints | POST | 约束条件分析 | region{}, timeRange{}, weather{} | riskFactors[], timeWindows[] | 规划中 |

4.3.4 INT-003 后端-MySQL数据库接口

a) 接口实体：Spring Boot后端应用、MySQL 8.0数据库
b) 通信协议：JDBC（MySQL Connector/J）
c) 连接配置：
   1) 地址：jdbc:mysql://36.103.234.242:8010/uygur_project
   2) 用户名：uygur_user
   3) 连接池：Druid（初始5连接，最大20连接，最大等待60秒）
d) 数据访问：通过MyBatis框架执行SQL映射，支持动态SQL和注解式SQL
e) 数据表：16张表（origin_text、text_type、extraction_result、fusion_report、intelligence_entity、intelligence_relation、intelligence_event、track_target、track_point、alert_rule、alert_record、recon_plan、recon_asset、recon_schedule、recon_approval、recon_result）

4.3.5 INT-004 后端-MinIO对象存储接口

a) 接口实体：Spring Boot后端应用、MinIO对象存储服务
b) 通信协议：HTTP/HTTPS（MinIO Java SDK）
c) 连接配置：
   1) 端点：http://36.103.234.242:8522
   2) 存储桶：xianwei-images
   3) 访问密钥：通过application.yml配置
d) 用途：存储图文报中的图片文件，前端通过拼接minioPrefix和文件名访问图片

4.3.6 INT-005 算法-LLM API接口

a) 接口实体：Flask算法服务、Paratera LLM平台
b) 通信协议：HTTPS（OpenAI SDK）
c) 连接配置：
   1) 基础URL：https://llmapi.paratera.com
   2) 模型：Qwen3.5-122B-A10B / GLM-5
   3) API密钥：通过config.json配置
   4) 温度：0.1，最大令牌数：16000
d) 调用场景：事件抽取、报文融合、实体消歧、关系推断、因果推理、方案生成、方案评估

4.3.7 INT-006 算法-分类服务接口

a) 接口实体：Flask算法服务、RexUniNlu分类服务
b) 通信协议：HTTP
c) 连接配置：
   1) 端点：http://36.103.234.242:8514
   2) 预定义标签：人物、组织、部队、军舰、火炮、战机、武器、装备
d) 用途：对抽取的主体实体进行多标签分类

4.3.8 INT-007 前端-后端WebSocket接口

a) 接口实体：Vue 3前端应用、Spring Boot后端应用
b) 通信协议：WebSocket（STOMP协议+SockJS兼容）
c) 端点清单：
   1) ws://api/track/realtime - 目标位置实时推送（每15秒）
   2) ws://api/alert/push - 实时告警通知推送（事件触发）
   3) ws://api/recon/monitor - 任务执行状态推送（每30秒）
d) 订阅模式：前端订阅特定主题，后端向订阅客户端推送消息

4.3.9 INT-008 后端-外部天气服务接口

a) 接口实体：Spring Boot后端应用、外部天气数据服务
b) 通信协议：HTTP/HTTPS
c) 用途：侦察筹划模块的约束条件分析，获取目标区域的天气预报数据

4.3.10 INT-009 后端-外部地图服务接口

a) 接口实体：Vue 3前端应用、外部地图瓦片服务
b) 通信协议：HTTP/HTTPS
c) 用途：情报分析、轨迹分析、侦察筹划模块的地图底图展示
