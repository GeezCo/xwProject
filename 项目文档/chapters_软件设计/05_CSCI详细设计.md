5 CSCI详细设计

5.1 SU-001 报文管理模块

5.1.1 单元概述

a) 单元标识：SU-001
b) 单元名称：报文管理模块
c) 单元用途：负责军事情报报文的增删改查、分类目录管理、分页查询、详情查看和批量导入功能，支持文字报和图文报两种模态类型
d) 开发状态：已实现（14个端点中6个已实现，8个规划中）
e) 编程语言：Java 8（Spring Boot 2.7.18框架）
f) 程序库位置：
   1) 控制器：后端/src/main/java/com/qy/dch/controller/UygurController.java
   2) 服务接口：后端/src/main/java/com/qy/dch/service/UygurService.java
   3) 服务实现：后端/src/main/java/com/qy/dch/service/impl/UygurServiceImpl.java
   4) 数据访问：后端/src/main/java/com/qy/dch/mapper/UygurMapper.java
   5) 数据传输对象：后端/src/main/java/com/qy/dch/dto/OriginTextDTO.java、TextTypeDTO.java
   6) 请求参数对象：后端/src/main/java/com/qy/dch/request/GetListRequest.java

5.1.2 设计决策

a) 采用Spring Boot + MyBatis + Druid连接池实现数据访问层
b) 使用PageHelper分页插件实现物理分页查询，避免内存溢出
c) 分类目录采用parent_id自关联实现树形结构，通过递归查询构建分类树
d) 图文报图片通过MinIO对象存储管理，前端动态获取MinIO地址前缀拼接URL
e) JSONL批量导入支持UTF-8、GBK、GB2312、UTF-16多编码自动识别（SD-005）

5.1.3 输入数据元素

表5-1 SU-001输入数据元素表

| 数据元素 | 数据类型 | 大小 | 值范围 | 来源 | 说明 |
|----------|----------|------|--------|------|------|
| pageNum | Integer | 4字节 | 1至正整数 | 前端请求 | 页码，从1开始 |
| pageSize | Integer | 4字节 | 1至100 | 前端请求 | 每页条数，默认10 |
| typeId | Integer | 4字节 | 正整数或null | 前端请求 | 分类ID，可选筛选条件 |
| modalType | String | 最长50字符 | 文字报/图文报 | 前端请求 | 模态类型，可选 |
| keyword | String | 最长200字符 | 任意字符串 | 前端请求 | 搜索关键词，可选 |
| sid | Long | 8字节 | 正整数 | 前端请求 | 报文唯一标识 |
| typeName | String | 最长100字符 | 任意字符串 | 前端请求 | 分类名称 |
| parentId | Integer | 4字节 | 0或正整数 | 前端请求 | 父分类ID，0为一级 |

5.1.4 输出数据元素

表5-2 SU-001输出数据元素表

| 数据元素 | 数据类型 | 说明 |
|----------|----------|------|
| ResultVO | Object | 统一响应格式，含code/data/msg |
| PageResultDTO | Object | 分页结果，含total/list/pageNum/pageSize |
| OriginTextDTO | Object | 报文实体，含sid/title/content/times/type/modalType/images/isExtracted |
| TextTypeDTO | Object | 分类实体，含id/typeName/parentId |
| minioPrefix | String | MinIO地址前缀，如http://36.141.21.176:8522/xianwei-images |

5.1.5 逻辑流程

　　报文列表查询的逻辑流程如下：

a) 执行启动条件：
   1) Spring Boot应用启动完成
   2) 数据库连接池初始化成功
   3) MyBatis映射文件加载完成

b) 操作顺序：
   1) 接收前端POST请求（/uygur/getTextList），解析GetListRequest参数
   2) 校验分页参数，pageNum默认为1，pageSize默认为10
   3) 调用PageHelper.startPage(pageNum, pageSize)设置分页上下文
   4) 根据typeId和modalType构建MyBatis动态SQL查询条件
   5) 执行分页查询，MyBatis返回PageInfo对象
   6) 将PageInfo封装为PageResultDTO（total、list、pageNum、pageSize）
   7) 包装为ResultVO返回JSON响应

c) 控制传递条件：
   1) 查询成功 → 返回ResultVO(code=1, data=PageResultDTO)
   2) 参数异常 → 返回ResultVO(code=0, msg="参数错误")
   3) 数据库异常 → 返回ResultVO(code=0, msg="查询失败")

d) 输入响应时间：
   1) 分页查询：小于200ms（正常负载）
   2) 分类树查询：小于100ms
   3) 报文详情查询：小于100ms

5.1.6 端点清单

表5-2.1 SU-001端点清单表

| # | 方法 | 路径 | 功能 | 状态 |
|---|------|------|------|------|
| 1 | GET | /uygur/category | 获取分类目录列表 | 已实现 |
| 2 | POST | /uygur/getTextList | 分页查询报文列表 | 已实现 |
| 3 | GET | /uygur/detail/{sid} | 获取报文详情 | 已实现 |
| 4 | POST | /uygur/importFromJsonl | 批量导入JSONL文件 | 已实现 |
| 5 | POST | /uygur/resetExtracted | 重置所有抽取状态 | 已实现 |
| 6 | GET | /uygur/config | 获取系统配置 | 已实现 |
| 7 | POST | /uygur/create | 创建新报文 | 规划中 |
| 8 | PUT | /uygur/update/{sid} | 更新报文 | 规划中 |
| 9 | DELETE | /uygur/delete/{sid} | 删除报文 | 规划中 |
| 10 | POST | /uygur/category/create | 创建分类 | 规划中 |
| 11 | PUT | /uygur/category/update/{id} | 更新分类 | 规划中 |
| 12 | DELETE | /uygur/category/delete/{id} | 删除分类 | 规划中 |
| 13 | POST | /uygur/batch/delete | 批量删除报文 | 规划中 |
| 14 | GET | /uygur/export | 导出报文数据 | 规划中 |

5.2 SU-002 属性抽取模块

5.2.1 单元概述

a) 单元标识：SU-002
b) 单元名称：属性抽取模块
c) 单元用途：接收前端的属性抽取请求，查询报文内容，调用算法服务执行基于大语言模型的事件抽取，将抽取结果持久化到数据库，并更新报文的抽取状态标志
d) 开发状态：已实现（2个端点全部实现）
e) 编程语言：Java 8（Spring Boot 2.7.18框架）
f) 程序库位置：
   1) 控制器：后端/src/main/java/com/qy/dch/controller/ExtractionController.java
   2) 服务实现：后端/src/main/java/com/qy/dch/service/impl/ExtractionServiceImpl.java
   3) 数据访问：后端/src/main/java/com/qy/dch/mapper/ExtractionMapper.java
   4) 数据传输对象：后端/src/main/java/com/qy/dch/dto/ExtractionResultDTO.java

5.2.2 设计决策

a) 采用同步调用算法服务的方式执行抽取，通过RestTemplate发起HTTP请求
b) 设置20分钟超时以适配LLM处理耗时（SD-002）
c) 实现缓存策略，已抽取的报文默认返回缓存结果，force=true时强制重新抽取（SD-003）
d) 抽取结果通过JSON字符串形式存储在extraction_result表的TEXT字段中

5.2.3 输入数据元素

表5-3 SU-002输入数据元素表

| 数据元素 | 数据类型 | 大小 | 值范围 | 来源 | 说明 |
|----------|----------|------|--------|------|------|
| originTextId | Long | 8字节 | 正整数 | 前端请求 | 报文唯一标识（origin_text.sid） |
| force | Boolean | 1字节 | true/false | 前端请求 | 是否强制重新抽取，默认false |

5.2.4 输出数据元素

表5-4 SU-002输出数据元素表

| 数据元素 | 数据类型 | 说明 |
|----------|----------|------|
| ExtractionResultDTO | Object | 抽取结果实体 |
| originTextId | Long | 关联的报文ID |
| eventsJson | String(TEXT) | 事件列表JSON，包含event_id/time/location/subject/action/labels/original_text |
| labelsJson | String(TEXT) | 标签列表JSON，如["战机","部队"] |
| extractionTime | Timestamp | 抽取完成时间 |

5.2.5 逻辑流程

a) 执行启动条件：
   1) Spring Boot应用启动完成
   2) RestTemplate初始化完成（20分钟超时配置）
   3) 算法服务（:5001）可达

b) 操作顺序：
   1) 接收前端POST请求（/extraction/extract），解析originTextId和force参数
   2) 通过UygurMapper查询origin_text表获取报文内容
   3) 检查缓存状态：若force=false且origin_text.is_extracted=1，直接从extraction_result表查询已有结果并返回
   4) 构建请求体：{"text": 报文content, "origin_text_id": originTextId}
   5) 通过RestTemplate发送POST请求到http://localhost:5001/extract
   6) 等待算法服务返回抽取结果（耗时约2至5分钟）
   7) 解析响应，构建ExtractionResultDTO对象
   8) 调用ExtractionMapper.insertResult()将结果写入extraction_result表
   9) 调用UygurMapper.updateExtractedStatus()将origin_text.is_extracted更新为1
   10) 返回抽取结果JSON

c) 控制传递条件：
   1) 抽取成功 → 返回ResultVO(code=1, data=抽取结果)
   2) 报文不存在 → 返回ResultVO(code=0, msg="报文不存在")
   3) 算法服务超时 → 返回ResultVO(code=0, msg="抽取超时")
   4) 算法服务异常 → 返回ResultVO(code=0, msg="抽取失败")

d) 输入响应时间：
   1) 缓存命中：小于200ms
   2) 首次抽取：2至5分钟（取决于报文长度和LLM响应速度）

5.2.6 端点清单

表5-4.1 SU-002端点清单表

| # | 方法 | 路径 | 功能 | 状态 |
|---|------|------|------|------|
| 1 | POST | /extraction/extract | 执行LLM事件抽取 | 已实现 |
| 2 | GET | /extraction/result/{originTextId} | 查询已保存抽取结果 | 已实现 |

5.3 SU-003 报文融合模块

5.3.1 单元概述

a) 单元标识：SU-003
b) 单元名称：报文融合模块
c) 单元用途：接收前端的融合请求，准备多篇源报文数据及其属性抽取结果，调用算法服务生成包含标题、摘要、时间线和详细内容的综合分析报告，支持报告的保存、列表查询、详情查看和导出
d) 开发状态：已实现（6个端点全部实现）
e) 编程语言：Java 8（Spring Boot 2.7.18框架）
f) 程序库位置：
   1) 控制器：后端/src/main/java/com/qy/dch/controller/FusionController.java
   2) 服务实现：后端/src/main/java/com/qy/dch/service/impl/FusionServiceImpl.java
   3) 数据访问：后端/src/main/java/com/qy/dch/mapper/FusionMapper.java
   4) 数据传输对象：后端/src/main/java/com/qy/dch/dto/FusionDTO.java
   5) 请求参数对象：后端/src/main/java/com/qy/dch/request/FusionCreateRequest.java、ReportData.java

5.3.2 设计决策

a) 融合报告生成分为两步：后端准备数据（含抽取结果），算法服务执行信息整合和LLM生成
b) 融合报告ID采用时间戳（fusionId = System.currentTimeMillis()），保证唯一性
c) 报告内容以Markdown格式存储，前端通过Marked库渲染展示
d) 支持PDF和Word两种导出格式，通过POI库实现

5.3.3 输入数据元素

表5-5 SU-003输入数据元素表

| 数据元素 | 数据类型 | 大小 | 值范围 | 来源 | 说明 |
|----------|----------|------|--------|------|------|
| reports | Array | 不定 | 至少2个元素 | 前端请求 | 源报文数据数组 |
| reports[].id | Long | 8字节 | 正整数 | 前端请求 | 报文ID |
| reports[].title | String | 最长500字符 | 任意字符串 | 前端请求 | 报文标题 |
| reports[].content | String(TEXT) | 不定 | 任意字符串 | 前端请求 | 报文内容 |
| fusionType | String | 最长50字符 | 预定义类型 | 前端请求 | 融合类型 |
| customTitle | String | 最长500字符 | 任意字符串 | 前端请求 | 自定义标题，可选 |
| pageNum | Integer | 4字节 | 正整数 | 前端请求 | 列表查询页码 |
| pageSize | Integer | 4字节 | 正整数 | 前端请求 | 列表查询每页条数 |

5.3.4 输出数据元素

表5-6 SU-003输出数据元素表

| 数据元素 | 数据类型 | 说明 |
|----------|----------|------|
| FusionDTO | Object | 融合报告实体 |
| fusionId | Long | 融合报告唯一ID（时间戳） |
| title | String | 融合报告标题 |
| summary | String(TEXT) | 摘要（约200字） |
| timelineJson | String(TEXT) | 时间线JSON，含time和description |
| content | String(TEXT) | 详细内容（Markdown格式） |
| entitiesJson | String(TEXT) | 实体列表JSON，按类别分组 |
| labelsJson | String(TEXT) | 标签列表JSON |
| sourceIds | String(TEXT) | 源报文ID列表JSON |

5.3.5 逻辑流程

a) 执行启动条件：
   1) Spring Boot应用启动完成
   2) 算法服务（:5001）可达
   3) 源报文均已完成属性抽取

b) 操作顺序（创建融合报告）：
   1) 接收前端POST请求（/api/fusion/create），解析FusionCreateRequest参数
   2) 遍历reports数组，对每篇报文查询其抽取结果（ExtractionMapper.selectByOriginTextId）
   3) 组装请求数据：每篇报文包含id、title、content和extractionResult
   4) 通过RestTemplate发送POST请求到http://localhost:5001/fusion/create
   5) 等待算法服务返回融合报告
   6) 解析响应，返回融合报告JSON（此时未持久化）

c) 操作顺序（保存融合报告）：
   1) 接收前端POST请求（/api/fusion/save），解析FusionDTO参数
   2) 生成fusionId（时间戳）
   3) 调用FusionMapper.insertReport()将报告写入fusion_report表
   4) 返回保存成功响应

d) 控制传递条件：
   1) 融合成功 → 返回ResultVO(code=1, data=融合报告)
   2) 源报文不足 → 返回ResultVO(code=0, msg="至少需要2篇报文")
   3) 算法服务异常 → 返回ResultVO(code=0, msg="融合失败")

e) 输入响应时间：
   1) 创建融合报告：1至3分钟（取决于报文数量和LLM响应速度）
   2) 保存报告：小于200ms
   3) 列表查询：小于200ms
   4) 导出Markdown：小于1秒

5.3.6 端点清单

表5-6.1 SU-003端点清单表

| # | 方法 | 路径 | 功能 | 状态 |
|---|------|------|------|------|
| 1 | POST | /api/fusion/create | 创建融合报告 | 已实现 |
| 2 | POST | /api/fusion/save | 保存融合报告 | 已实现 |
| 3 | GET | /api/fusion/list | 查询融合报告列表 | 已实现 |
| 4 | GET | /api/fusion/detail/{id} | 查询融合报告详情 | 已实现 |
| 5 | GET | /api/fusion/export/{id} | 导出融合报告为Markdown | 已实现 |
| 6 | DELETE | /api/fusion/delete/{id} | 删除融合报告 | 已实现 |

5.4 SU-004 数据统计模块

5.4.1 单元概述

a) 单元标识：SU-004
b) 单元名称：数据统计模块
c) 单元用途：提供报文、抽取、融合等多维度统计分析，包括数据概览、分类分布、时间分布、热门标签等统计功能
d) 开发状态：规划中
e) 编程语言：Java 8（Spring Boot 2.7.18框架）
f) 程序库位置：
   1) 控制器：后端/src/main/java/com/qy/dch/controller/StatisticsController.java（规划中）
   2) 服务实现：后端/src/main/java/com/qy/dch/service/impl/StatisticsServiceImpl.java（规划中）
   3) 数据访问：后端/src/main/java/com/qy/dch/mapper/StatisticsMapper.java（规划中）

5.4.2 设计决策

a) 使用MySQL的GROUP BY和聚合函数实现统计
b) 时间分布统计支持按日、周、月、年聚合
c) 标签统计基于extraction_result表的labels_json字段
d) 分类分布统计基于origin_text表的type字段
e) 数据概览提供总数、已抽取数、融合报告数等关键指标

5.4.3 端点清单

表5-7 SU-004端点清单表

| # | 方法 | 路径 | 功能 | 状态 |
|---|------|------|------|------|
| 1 | GET | /statistics/overview | 获取数据概览 | 规划中 |
| 2 | GET | /statistics/category | 获取分类分布统计 | 规划中 |
| 3 | GET | /statistics/timeline | 获取时间分布统计 | 规划中 |
| 4 | GET | /statistics/labels | 获取热门标签统计 | 规划中 |
| 5 | GET | /statistics/extraction | 获取抽取统计 | 规划中 |

5.5 SU-005 情报分析模块

5.5.1 单元概述

a) 单元标识：SU-005
b) 单元名称：情报分析模块
c) 单元用途：基于抽取结果构建知识图谱和事件时间线，支持实体关系查询、热力图展示和区域详情分析。通过LLM进行实体消歧和关系推断，构建高质量的知识图谱
d) 开发状态：规划中
e) 编程语言：Java 8（Spring Boot 2.7.18框架）
f) 程序库位置：
   1) 控制器：后端/src/main/java/com/qy/dch/controller/IntelligenceController.java（规划中）
   2) 服务实现：后端/src/main/java/com/qy/dch/service/impl/IntelligenceServiceImpl.java（规划中）
   3) 数据访问：后端/src/main/java/com/qy/dch/mapper/IntelligenceMapper.java（规划中）

5.5.2 设计决策

a) 知识图谱采用离线构建+增量更新的方式（SD-011）
b) 实体消歧和关系推断通过LLM实现
c) 时间线构建包含因果关系推断和重要性评估
d) 热力图基于地点实体的频次统计
e) 支持实体关系的多跳查询

5.5.3 端点清单

表5-8 SU-005端点清单表

| # | 方法 | 路径 | 功能 | 状态 |
|---|------|------|------|------|
| 1 | POST | /intelligence/graph/build | 构建知识图谱 | 规划中 |
| 2 | GET | /intelligence/graph/query | 查询实体关系 | 规划中 |
| 3 | POST | /intelligence/timeline/build | 构建事件时间线 | 规划中 |
| 4 | GET | /intelligence/timeline/query | 查询时间线 | 规划中 |
| 5 | GET | /intelligence/heatmap | 获取热力图数据 | 规划中 |
| 6 | GET | /intelligence/region/{name} | 获取区域详情 | 规划中 |
| 7 | GET | /intelligence/entity/{name} | 获取实体详情 | 规划中 |

5.6 SU-006 轨迹分析模块

5.6.1 单元概述

a) 单元标识：SU-006
b) 单元名称：轨迹分析模块
c) 单元用途：对目标轨迹进行查询、预测、回放和异常检测，支持行军统计、集结地分析和路线分析。采用卡尔曼滤波进行轨迹预测，使用孤立森林算法进行异常检测
d) 开发状态：规划中
e) 编程语言：Java 8（Spring Boot 2.7.18框架）
f) 程序库位置：
   1) 控制器：后端/src/main/java/com/qy/dch/controller/TrackController.java（规划中）
   2) 服务实现：后端/src/main/java/com/qy/dch/service/impl/TrackServiceImpl.java（规划中）
   3) 数据访问：后端/src/main/java/com/qy/dch/mapper/TrackMapper.java（规划中）

5.6.2 设计决策

a) 轨迹点数据采用时间序列存储，支持高效范围查询
b) 卡尔曼滤波用于轨迹预测和平滑（SD-012）
c) 孤立森林算法用于异常轨迹检测（SD-013）
d) 集结地识别基于DBSCAN聚类算法
e) 路线分析基于轨迹相似度计算

5.6.3 端点清单

表5-9 SU-006端点清单表

| # | 方法 | 路径 | 功能 | 状态 |
|---|------|------|------|------|
| 1 | GET | /track/query | 查询目标轨迹 | 规划中 |
| 2 | POST | /track/predict | 预测目标轨迹 | 规划中 |
| 3 | GET | /track/playback | 轨迹回放 | 规划中 |
| 4 | GET | /track/statistics | 行军统计 | 规划中 |
| 5 | POST | /track/pattern/areas | 集结地分析 | 规划中 |
| 6 | POST | /track/pattern/time | 时间规律分析 | 规划中 |
| 7 | POST | /track/pattern/routes | 路线分析 | 规划中 |
| 8 | POST | /track/pattern/anomalies | 异常检测 | 规划中 |
| 9 | POST | /track/pattern/similar | 相似轨迹查询 | 规划中 |
| 10 | POST | /track/import | 批量导入轨迹数据 | 规划中 |

5.7 SU-007 告警管理模块

5.7.1 单元概述

a) 单元标识：SU-007
b) 单元名称：告警管理模块
c) 单元用途：提供告警规则配置、实时告警推送和历史查询功能。支持多种告警类型（轨迹异常、区域入侵、活动频繁、情报缺口），通过WebSocket实现实时推送
d) 开发状态：规划中
e) 编程语言：Java 8（Spring Boot 2.7.18框架）
f) 程序库位置：
   1) 控制器：后端/src/main/java/com/qy/dch/controller/AlertController.java（规划中）
   2) 服务实现：后端/src/main/java/com/qy/dch/service/impl/AlertServiceImpl.java（规划中）
   3) 数据访问：后端/src/main/java/com/qy/dch/mapper/AlertMapper.java（规划中）
   4) WebSocket配置：后端/src/main/java/com/qy/dch/config/WebSocketConfig.java（规划中）

5.7.2 设计决策

a) 告警规则支持条件表达式配置
b) 使用WebSocket（STOMP协议）实现实时推送（SD-010）
c) 告警记录包含状态流转（未处理/已确认/已忽略/已升级）
d) 支持告警级别（严重/警告/提示）
e) 告警处理支持批量操作

5.7.3 端点清单

表5-10 SU-007端点清单表

| # | 方法 | 路径 | 功能 | 状态 |
|---|------|------|------|------|
| 1 | POST | /alert/rule/create | 创建告警规则 | 规划中 |
| 2 | PUT | /alert/rule/update/{id} | 更新告警规则 | 规划中 |
| 3 | DELETE | /alert/rule/delete/{id} | 删除告警规则 | 规划中 |
| 4 | GET | /alert/rule/list | 查询告警规则列表 | 规划中 |
| 5 | PUT | /alert/rule/toggle/{id} | 启用/禁用规则 | 规划中 |
| 6 | GET | /alert/record/list | 查询告警记录列表 | 规划中 |
| 7 | PUT | /alert/record/handle/{id} | 处理告警 | 规划中 |
| 8 | POST | /alert/record/batch/handle | 批量处理告警 | 规划中 |
| 9 | GET | /alert/statistics | 获取告警统计 | 规划中 |

5.8 SU-008 侦察筹划模块

5.8.1 单元概述

a) 单元标识：SU-008
b) 单元名称：侦察筹划模块
c) 单元用途：支持情报缺口识别、侦察计划生成、资源调度和计划评估功能。通过LLM辅助生成侦察方案，并提供多维度评估（可行性、风险、效率、时效性）
d) 开发状态：规划中
e) 编程语言：Java 8（Spring Boot 2.7.18框架）
f) 程序库位置：
   1) 控制器：后端/src/main/java/com/qy/dch/controller/ReconController.java（规划中）
   2) 服务实现：后端/src/main/java/com/qy/dch/service/impl/ReconServiceImpl.java（规划中）
   3) 数据访问：后端/src/main/java/com/qy/dch/mapper/ReconMapper.java（规划中）

5.8.2 设计决策

a) 侦察方案生成采用五步骤流程（明确任务→分析情况→确定方法→组织实施→保障措施）（SD-014）
b) LLM辅助识别情报缺口和生成方案
c) 资源调度支持时间冲突检测
d) 方案评估包含四个维度的量化评分（SD-015）
e) 审批流程支持多级审批和流转

5.8.3 端点清单

表5-11 SU-008端点清单表

| # | 方法 | 路径 | 功能 | 状态 |
|---|------|------|------|------|
| 1 | POST | /recon/plan/create | 创建侦察方案 | 规划中 |
| 2 | PUT | /recon/plan/update/{id} | 更新侦察方案 | 规划中 |
| 3 | DELETE | /recon/plan/delete/{id} | 删除侦察方案 | 规划中 |
| 4 | GET | /recon/plan/list | 查询侦察方案列表 | 规划中 |
| 5 | GET | /recon/plan/detail/{id} | 获取侦察方案详情 | 规划中 |
| 6 | POST | /recon/plan/generate | AI生成侦察方案 | 规划中 |
| 7 | POST | /recon/plan/evaluate/{id} | 评估侦察方案 | 规划中 |
| 8 | POST | /recon/gaps/identify | 识别情报缺口 | 规划中 |
| 9 | GET | /recon/asset/list | 查询侦察资源列表 | 规划中 |
| 10 | POST | /recon/schedule/create | 创建调度任务 | 规划中 |
| 11 | GET | /recon/schedule/list | 查询调度任务列表 | 规划中 |
| 12 | POST | /recon/approval/submit/{id} | 提交审批 | 规划中 |
| 13 | POST | /recon/approval/approve/{id} | 审批通过 | 规划中 |
| 14 | POST | /recon/approval/reject/{id} | 审批驳回 | 规划中 |
| 15 | GET | /recon/result/list | 查询侦察成果列表 | 规划中 |

5.9 SU-009 算法服务模块

5.9 SU-009 算法服务模块

5.9.1 单元概述

a) 单元标识：SU-009
b) 单元名称：算法服务模块
c) 单元用途：提供基于大语言模型的核心算法能力，包括事件抽取、报文融合、知识图谱构建、轨迹预测、侦察筹划等算法能力。通过Flask框架提供REST API供后端调用
d) 开发状态：部分实现（事件抽取和报文融合已实现，其他功能规划中）
e) 编程语言：Python 3.10+
f) 程序库位置：
   1) Flask REST API：算法/算法服务/app.py
   2) V3事件抽取器：算法/数据抽取-新/llm_event_extractor_v3.py
   3) 融合算法：算法/报文融合/fusion_extractor.py
   4) 分类客户端：算法/数据抽取-新/classify_client.py
   5) LLM配置：算法/数据抽取-新/config.json

　　SU-009包含以下4个子单元：

5.9.2 SU-009-1 LLMEventExtractorV3（V3事件抽取器）

a) 子单元用途：对输入文本进行基于大语言模型的军事事件抽取，提取时间、地点、主体（按8类标签分类）、动作和原文等信息
b) 设计决策：
   1) 采用一次性抽取加分类策略，在单个LLM调用中同时完成事件抽取和主体分类，减少API调用次数
   2) 输入文本预处理：转义引号（双引号和单引号），防止LLM输出的JSON包含未转义引号导致解析失败
   3) 三重JSON解析策略（SD-004）：直接解析→Markdown代码块提取→花括号计数
   4) 状态机修复：处理不完整JSON
c) 输入数据元素：
   1) text：String，待抽取的报文文本内容
   2) origin_text_id：Long，报文唯一标识
d) 输出数据元素：
   1) events：Array，事件列表，每个事件包含event_id、time、location[]、subject[]、action、labels[]、船只[]、飞机[]、武器[]等字段
   2) labels：Array，全局标签列表
   3) entities：Object，实体分类映射
   4) llm_calls：Integer，LLM调用次数
   5) llm_calls_saved：Integer，节省的LLM调用次数
e) 逻辑流程：
   1) 输入预处理：转义引号
   2) 段落分割：按换行符分割文本
   3) Prompt构建：构造LLM提示词
   4) LLM调用：调用OpenAI SDK
   5) 三重JSON解析：直接解析→Markdown代码块提取→花括号计数
   6) 后处理：生成subject和labels字段
   7) 调用RexUniNlu分类服务进行多标签分类
   8) 返回抽取结果

5.9.3 SU-009-2 FusionExtractor（报文融合算法）

a) 子单元用途：将多篇相关报文的抽取结果进行融合，生成综合分析报告
b) 设计决策：
   1) 数据预处理：统一字段格式
   2) 信息整合：合并时间线、实体、标签（代码实现）
   3) LLM生成：生成标题、摘要、详细内容
   4) 结果组装：输出融合报告
c) 输入数据元素：
   1) reports：Array，源报文列表，每篇包含id、title、content、extractionResult
   2) fusionType：String，融合类型
   3) customTitle：String，自定义标题（可选）
d) 输出数据元素：
   1) fusionId：Long，融合报告唯一标识（时间戳）
   2) title：String，融合报告标题
   3) summary：String，摘要
   4) timeline：Array，时间线
   5) content：String，详细内容（Markdown格式）
   6) entities：Object，实体分类映射
   7) labels：Array，标签列表
   8) sourceIds：Array，源报文ID列表
   9) modelUsed：String，使用的LLM模型
   10) createTime：String，创建时间
e) 逻辑流程：
   1) 数据预处理：统一字段格式
   2) 信息整合：合并时间线、实体、标签
   3) LLM生成：生成标题、摘要、详细内容
   4) 结果组装：输出融合报告

5.9.4 SU-009-3 ClassifyClient（RexUniNlu分类客户端）

a) 子单元用途：调用RexUniNlu服务进行多标签分类
b) 设计决策：
   1) HTTP客户端调用外部分类服务
   2) 支持8类预定义标签：人物、组织、部队、军舰、火炮、战机、武器、装备
c) 输入数据元素：
   1) texts：Array，主体列表
   2) labels：Array，候选标签列表
d) 输出数据元素：
   1) 每个主体的分类结果
e) 逻辑流程：
   1) 构建HTTP请求
   2) 调用RexUniNlu服务（http://36.103.234.242:8514）
   3) 解析响应结果
   4) 返回分类结果

5.9.5 SU-009-4 Flask REST API（路由分发）

a) 子单元用途：提供HTTP REST接口，接收后端请求并分发到相应的算法组件
b) 设计决策：
   1) 采用Flask框架，轻量级且适合Python算法服务
   2) 启用flask-cors支持跨域请求
   3) 配置RotatingFileHandler日志轮转（10MB单文件，保留5份备份）
   4) 支持多模型LLM（Qwen3.5-122B-A10B、GLM-5）
c) 端点路由（已实现）：
   1) GET /health → 返回服务状态、LLM配置和当前时间
   2) POST /extract → 调用LLMEventExtractorV3执行事件抽取
   3) POST /extract/simple → 调用LLMEventExtractorV3执行简单抽取（无缓存）
   4) POST /fusion/create → 调用FusionExtractor执行报文融合
d) 端点路由（规划中）：
   5) POST /intelligence/build_graph → 构建知识图谱
   6) POST /intelligence/build_timeline → 构建事件时间线
   7) POST /track/predict → 轨迹预测
   8) POST /track/pattern/areas → 集结地分析
   9) POST /track/pattern/time → 时间规律分析
   10) POST /track/pattern/routes → 路线分析
   11) POST /track/pattern/anomalies → 异常检测
   12) POST /track/pattern/similar → 相似轨迹查询
   13) POST /recon/identify_gaps → 识别情报缺口
   14) POST /recon/generate_plan → 生成侦察方案
   15) POST /recon/evaluate_plan → 评估侦察方案
   16) POST /recon/analyze_constraints → 分析约束条件
e) 逻辑流程：
   1) Flask应用启动，监听5001端口
   2) 初始化LLMEventExtractorV3和FusionExtractor实例，加载config.json配置
   3) 配置日志：控制台输出 + 文件轮转（算法服务/logs/algorithm.log）
   4) 接收HTTP请求，根据路由分发到对应处理函数
   5) 处理函数调用算法组件执行业务逻辑
   6) 捕获异常，返回错误响应（HTTP 500）
   7) 返回JSON格式的处理结果

5.9.6 端点清单

表5-12 SU-009端点清单表

| # | 方法 | 路径 | 功能 | 状态 |
|---|------|------|------|------|
| 1 | GET | /health | 健康检查 | 已实现 |
| 2 | POST | /extract | 执行事件抽取 | 已实现 |
| 3 | POST | /extract/simple | 简化版事件抽取 | 已实现 |
| 4 | POST | /fusion/create | 创建融合报告 | 已实现 |
| 5 | POST | /intelligence/build_graph | 构建知识图谱 | 规划中 |
| 6 | POST | /intelligence/build_timeline | 构建事件时间线 | 规划中 |
| 7 | POST | /track/predict | 轨迹预测 | 规划中 |
| 8 | POST | /track/pattern/areas | 集结地分析 | 规划中 |
| 9 | POST | /track/pattern/time | 时间规律分析 | 规划中 |
| 10 | POST | /track/pattern/routes | 路线分析 | 规划中 |
| 11 | POST | /track/pattern/anomalies | 异常检测 | 规划中 |
| 12 | POST | /track/pattern/similar | 相似轨迹查询 | 规划中 |
| 13 | POST | /recon/identify_gaps | 识别情报缺口 | 规划中 |
| 14 | POST | /recon/generate_plan | 生成侦察方案 | 规划中 |
| 15 | POST | /recon/evaluate_plan | 评估侦察方案 | 规划中 |
| 16 | POST | /recon/analyze_constraints | 分析约束条件 | 规划中 |
   2) total：Integer，事件总数
e) 逻辑流程：
   1) 接收输入文本
   2) 执行输入预处理：将双引号替换为转义双引号，将单引号替换为转义单引号
   3) 构建Prompt：要求LLM从文本中抽取军事事件，每个事件按8类标签（人物、组织、部队、军舰、火炮、战机、武器、装备）分类主体
   4) 调用LLM API（OpenAI SDK的ChatCompletion.create），参数为temperature=0.1、max_tokens=16000
   5) 接收LLM响应文本，兼容GLM-5的reasoning_content字段
   6) 使用三重解析策略解析JSON响应
   7) 后处理：遍历每个事件，从8个类别字段中提取所有主体合并为subject数组，提取非空类别名称为labels数组
   8) 返回events列表和total计数

5.4.3 SU-004-2 FusionExtractor（融合算法）

a) 子单元用途：将多篇相关报文的抽取结果进行融合，通过信息整合和LLM生成产出综合分析报告
b) 设计决策：
   1) 融合过程分为代码整合和LLM生成两个阶段，代码层面完成时间线合并、实体聚合和标签合并，LLM负责生成标题、摘要和详细内容
   2) 时间线按时间排序并去重
   3) 实体按类别分组聚合并去重
   4) LLM生成温度参数设为0.3，略高于抽取（0.1），允许更多创造性
c) 输入数据元素：
   1) reports：Array，源报文数据数组，每个报文包含id、title、content和extractionResult
   2) fusionType：String，融合类型
d) 输出数据元素：
   1) fusionId：Long，融合报告唯一ID（毫秒级时间戳）
   2) title：String，融合报告标题
   3) summary：String，200字左右的摘要
   4) timeline：Array，时间线列表，每项含time和description
   5) content：String，详细内容（Markdown格式，包含背景、事件经过、影响分析）
   6) entities：Object，按类别分组的实体字典
   7) labels：Array，标签列表
   8) sourceIds：Array，源报文ID列表
e) 逻辑流程：
   1) 数据预处理：遍历源报文，解析每篇报文的extractionResult中的eventsJson和labelsJson
   2) 信息整合（代码层面）：
      ① 合并时间线：从所有事件中提取time和action，构建时间点描述
      ② 合并实体：按类别（人物、组织、部队等）分组，使用Set去重
      ③ 合并标签：收集所有报文的标签，使用Set去重
   3) 时间线排序：按时间字段排序
   4) LLM生成：构建Prompt，将源报文摘要提供给LLM，要求生成标题、摘要和详细内容（Markdown格式）
   5) 调用LLM API（temperature=0.3，max_tokens=8000）
   6) 解析LLM响应，提取title、summary和content
   7) 结果组装：将代码整合的timeline、entities、labels与LLM生成的title、summary、content合并，添加fusionId和sourceIds
   8) 返回完整融合报告

5.4.4 SU-004-3 ClassifyClient（分类客户端）

a) 子单元用途：调用RexUniNlu外部服务对事件中的主体进行多标签分类
b) 设计决策：预定义8类候选标签，通过HTTP POST请求调用外部分类服务
c) 输入数据元素：
   1) subjects：Array，待分类的主体名称列表
d) 输出数据元素：
   1) 分类结果：每个主体对应的标签列表
e) 逻辑流程：
   1) 构建请求体：{"texts": subjects, "labels": ["人物","组织","部队","军舰","火炮","战机","武器","装备"]}
   2) 发送HTTP POST请求到http://36.103.234.242:8514
   3) 解析响应JSON，返回分类结果

5.4.5 SU-004-4 Flask REST API（路由分发）

a) 子单元用途：提供HTTP REST接口，接收后端请求并分发到相应的算法组件
b) 设计决策：
   1) 采用Flask框架，轻量级且适合Python算法服务
   2) 启用flask-cors支持跨域请求
   3) 配置RotatingFileHandler日志轮转（10MB单文件，保留5份备份）
c) 端点路由：
   1) GET /health → 返回服务状态、名称和当前时间
   2) POST /extract → 调用LLMEventExtractorV3执行事件抽取
   3) POST /extract/simple → 调用LLMEventExtractorV3执行简单抽取（无缓存）
   4) POST /fusion/create → 调用FusionExtractor执行报文融合
d) 逻辑流程：
   1) Flask应用启动，监听5001端口
   2) 初始化LLMEventExtractorV3和FusionExtractor实例，加载config.json配置
   3) 配置日志：控制台输出 + 文件轮转（算法服务/logs/algorithm.log）
   4) 接收HTTP请求，根据路由分发到对应处理函数
   5) 处理函数调用算法组件执行业务逻辑
   6) 捕获异常，返回错误响应（HTTP 500）
   7) 返回JSON格式的处理结果
