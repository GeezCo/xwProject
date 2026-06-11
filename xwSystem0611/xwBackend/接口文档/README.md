# 献微系统接口文档

## 文档结构

本目录包含献微系统的完整后端接口文档，按功能模块拆分为以下章节：

- [概述](概述.md) - 服务地址与统一响应格式
- [1.报文管理接口](1.报文管理接口.md) - UygurController，报文查询、分类、导入
- [2.属性抽取接口](2.属性抽取接口.md) - ExtractionController，单篇抽取、批量抽取
- [3.报文融合接口](3.报文融合接口.md) - FusionController，融合创建、保存、查询、导出
- [4.数据统计看板接口](4.数据统计看板接口.md) - DashboardController，核心指标、分布统计
- [5.算法服务接口](5.算法服务接口.md) - Flask app.py，事件抽取、报文融合、事件拆分
- [6.调用关系](6.调用关系.md) - 前后端与算法服务的调用流程图
- [7.每日要情接口](7.每日要情接口.md) - EventAnalysisController，事件分析、定时任务
- [8.分类管理接口](8.分类管理接口.md) - CategoryController，多级树形分类管理 ✨ 新增

## 快速索引

### 报文管理 (UygurController)
- `GET /uygur/category` - 获取分类列表（已废弃，请使用 `/api/category/tree`）
- `POST /uygur/getTextList` - 分页查询报文
- `GET /uygur/detail/{sid}` - 获取报文详情
- `POST /uygur/addCategory` - 新增分类（已废弃，请使用 `/api/category/create`）
- `POST /uygur/importFromJsonl` - 导入JSONL文件（参数已优化：移除中文参数，支持自动分类）

### 分类管理 (CategoryController) ✨ 新增
- `GET /api/category/tree` - 获取完整分类树
- `GET /api/category/leafs` - 获取所有叶子节点
- `POST /api/category/create` - 新增分类节点
- `PUT /api/category/update` - 更新分类（重命名、修改描述）
- `POST /api/category/move` - 移动分类节点
- `DELETE /api/category/delete/{id}` - 删除分类（级联）
- `GET /api/category/detail/{id}` - 获取分类详情

### 属性抽取 (ExtractionController)
- `POST /extraction/extract` - 执行单篇抽取
- `GET /extraction/result/{id}` - 查询抽取结果
- `POST /extraction/batch/start` - 启动批量抽取
- `GET /extraction/batch/progress/{taskId}` - 查询批量进度
- `POST /extraction/batch/stop/{taskId}` - 停止批量任务

### 报文融合 (FusionController)
- `POST /api/fusion/create` - 创建融合报告
- `POST /api/fusion/save` - 保存融合报告
- `GET /api/fusion/list` - 查询融合列表
- `GET /api/fusion/detail/{id}` - 查询融合详情
- `GET /api/fusion/export/{id}` - 导出Markdown
- `POST /api/fusion/searchByTarget` - 目标搜索融合

### 数据统计看板 (DashboardController)
- `GET /api/dashboard/overview` - 核心指标概览
- `GET /api/dashboard/categoryDistribution` - 分类分布
- `GET /api/dashboard/modalDistribution` - 模态分布
- `GET /api/dashboard/recentFusions` - 最近融合报告

### 每日要情 (EventAnalysisController) ✨ 新增
- `POST /api/eventAnalysis/trigger` - 手动触发分析任务
- `POST /api/eventAnalysis/query` - 查询事件（按关键词筛选）
- `GET /api/eventAnalysis/status` - 查询分析任务状态
- 定时任务：每日凌晨2点自动分析前一天报文

### 算法服务 (Flask :5001)
- `GET /health` - 健康检查
- `POST /extract` - 事件抽取（完整版）
- `POST /extract/simple` - 事件抽取（简化版）
- `POST /fusion/create` - 报文融合
- `POST /eventSplit` - 事件拆分与分析 ✨ 新增

## 版本历史

- **v1.5** (2026-06-10) - 优化JSONL导入接口（移除中文参数）、新增分类管理接口（多级树形结构）
- **v1.4** (2026-06-08) - 新增每日要情接口、事件拆分接口、后台定时分析任务
- **v1.3** (2026-05-19) - 新增数据统计看板接口、批量抽取接口、目标搜索融合接口
- **v1.2** (2026-04-22) - 完善报文融合功能，新增导出接口
- **v1.1** (2026-04-18) - 新增JSONL导入、分类管理功能
- **v1.0** (2026-04-15) - 初始版本，基础报文管理与属性抽取功能

## 服务地址

- **后端服务**: `http://localhost:8081`
- **算法服务**: `http://localhost:5001`
- **前端服务**: `http://localhost:8080`

## 统一响应格式

```json
{
  "code": 1,
  "msg": "success",
  "data": {...}
}
```

- `code`: 1=成功，0=失败
- `msg`: 响应消息
- `data`: 响应数据
