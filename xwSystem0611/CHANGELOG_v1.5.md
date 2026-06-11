# 献微系统更新日志 - v1.5

## 更新时间
2026-06-10

## 核心更新

### 1. 多级分类体系重构 ✨
- **数据库重构**：`text_type` 表改为邻接表+物化路径混合模式
- **支持5层分类**：根节点 → 4层子节点
- **全局唯一名称**：分类名称不可重复
- **物化路径**：`full_path` 字段存储完整路径（如 `根分类/美俄/美国/国防部`）

### 2. 分类管理接口 ✨
新增 `CategoryController`（`/api/category/*`）：
- `GET /api/category/tree` - 获取完整分类树
- `GET /api/category/leafs` - 获取所有叶子节点
- `POST /api/category/create` - 新增分类
- `PUT /api/category/update` - 更新分类
- `POST /api/category/move` - 移动分类节点
- `DELETE /api/category/delete/{id}` - 级联删除
- `GET /api/category/detail/{id}` - 获取详情

### 3. JSONL导入接口优化 ✨
**接口**: `POST /uygur/importFromJsonl`

**修改前**：
```
参数: file, parentCategoryName, categoryName
问题: 中文参数编码错误（multipart 表单）
```

**修改后**：
```
参数: file, defaultCategoryId (可选，默认2)
优势: 
  - 移除中文参数，解决编码问题
  - 支持 sendUnitName 自动分类
  - 简化调用
```

**自动分类逻辑**：
1. JSONL 中有 `sendUnitName` → 自动创建/匹配对应分类节点
2. JSONL 中无 `sendUnitName` → 使用 `defaultCategoryId` 指定的分类

### 4. 响应编码优化
- CategoryController 添加 `produces = "application/json; charset=UTF-8"`
- 新创建节点的中文字段正确显示

## 数据库变更

### text_type 表结构
```sql
CREATE TABLE text_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,      -- 分类名称
    parent_id BIGINT DEFAULT NULL,          -- 父节点ID
    level TINYINT NOT NULL DEFAULT 1,       -- 层级(1-5)
    full_path VARCHAR(500) NOT NULL,        -- 完整路径
    sort_order INT DEFAULT 0,
    is_leaf TINYINT(1) DEFAULT 0,           -- 是否叶子节点
    description VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### origin_text 表新增字段
- `send_unit_name` VARCHAR(100) - 送报单位
- `brief_type_name` VARCHAR(100) - 报文类型

## 接口文档更新

- 新增：[8.分类管理接口.md](后端/接口文档/8.分类管理接口.md)
- 更新：[1.报文管理接口.md](后端/接口文档/1.报文管理接口.md) - 1.7节 JSONL导入
- 更新：[README.md](后端/接口文档/README.md) - 版本历史、接口索引

## 测试验证

### 已验证功能 ✅
- JSONL 导入（5条记录全部成功）
- 自动分类（根据 sendUnitName）
- 分类节点创建
- 响应编码（新节点中文正确）

### 已知限制
- 初始数据（根分类、未分类）中文显示有历史编码问题
- 不影响新创建节点和业务功能

## 迁移指南

### 前端调用变更

**旧代码**：
```python
files = {'file': open('data.jsonl', 'rb')}
data = {
    'parentCategoryName': '根分类',  # 中文参数
    'categoryName': '未分类'
}
requests.post(url, files=files, data=data)
```

**新代码**：
```python
files = {'file': open('data.jsonl', 'rb')}
# 不传参数，使用默认分类
requests.post(url, files=files)

# 或指定默认分类ID
data = {'defaultCategoryId': 2}
requests.post(url, files=files, data=data)
```

### 分类查询变更

**旧接口（已废弃）**：
```
GET /uygur/category
```

**新接口**：
```
GET /api/category/tree
```

## 版本兼容性

- **后端**: 向后兼容（旧接口保留，返回错误提示）
- **数据库**: 需要执行 `refactor_category.sql` 迁移脚本
- **前端**: 需要更新 API 调用路径

## 贡献者
- 数据库重构：2026-06-09
- 接口优化：2026-06-10
- 文档更新：2026-06-10
