# 示例文档：项目开发流程

本文档演示Markdown转Word功能，包含流程图、甘特图、ER图和表格。

---

## 一、项目开发流程图

以下是标准的项目开发流程：

```mermaid
flowchart TD
    A[项目启动] --> B[需求分析]
    B --> C[系统设计]
    C --> D[详细设计]
    D --> E[编码开发]
    E --> F[单元测试]
    F --> G{测试通过?}
    G -->|是| H[集成测试]
    G -->|否| E
    H --> I[用户验收测试]
    I --> J[部署上线]
    J --> K[项目维护]
    K --> L[项目结项]

    style A fill:#e1f5e1
    style L fill:#ffe1e1
```

## 二、开发时间甘特图

项目各阶段时间安排：

```mermaid
gantt
    title 项目开发进度计划
    dateFormat  YYYY-MM-DD
    section 需求阶段
    需求调研     :a1, 2024-01-01, 7d
    需求分析     :a2, after a1, 5d
    需求评审     :a3, after a2, 2d
    section 设计阶段
    系统设计     :b1, after a3, 5d
    详细设计     :b2, after b1, 7d
    设计评审     :b3, after b2, 2d
    section 开发阶段
    编码开发     :c1, after b3, 14d
    单元测试     :c2, after c1, 5d
    section 测试阶段
    集成测试     :d1, after c2, 5d
    验收测试     :d2, after d1, 3d
```

## 三、数据库ER图

系统核心实体关系：

```mermaid
erDiagram
    USER ||--o{ ORDER : places
    USER {
        int id PK
        string username
        string email
        string password_hash
        datetime created_at
    }
    ORDER ||--|{ ORDER_ITEM : contains
    ORDER {
        int id PK
        int user_id FK
        datetime order_date
        string status
        decimal total_amount
    }
    PRODUCT ||--o{ ORDER_ITEM : "included in"
    PRODUCT {
        int id PK
        string name
        string description
        decimal price
        int stock
    }
    ORDER_ITEM {
        int id PK
        int order_id FK
        int product_id FK
        int quantity
        decimal unit_price
    }
```

## 四、类图设计

系统核心类结构：

```mermaid
classDiagram
    class Animal {
        +String name
        +int age
        +makeSound()
    }
    class Dog {
        +String breed
        +bark()
        +fetch()
    }
    class Cat {
        +String color
        +meow()
        +scratch()
    }
    Animal <|-- Dog
    Animal <|-- Cat

    class Person {
        -String name
        -Animal pet
        +adoptPet(Animal)
        +feedPet()
    }
    Person "1" --> "0..1" Animal : has
```

## 五、时序图

用户登录流程：

```mermaid
sequenceDiagram
    participant U as 用户
    participant F as 前端
    participant B as 后端
    participant D as 数据库
    participant C as 缓存

    U->>F: 输入用户名密码
    F->>B: POST /api/login
    B->>D: 查询用户信息
    D-->>B: 返回用户数据

    alt 用户存在
        B->>B: 验证密码
        alt 密码正确
            B->>C: 存储会话Token
            C-->>B: 存储成功
            B-->>F: 返回Token
            F-->>U: 登录成功
        else 密码错误
            B-->>F: 密码错误
            F-->>U: 提示密码错误
        end
    else 用户不存在
        B-->>F: 用户不存在
        F-->>U: 提示用户不存在
    end
```

## 六、状态图

订单状态流转：

```mermaid
stateDiagram-v2
    [*] --> 待支付
    待支付 --> 已支付 : 支付成功
    待支付 --> 已取消 : 取消订单
    已支付 --> 待发货 : 确认订单
    待发货 --> 已发货 : 发货
    已发货 --> 已签收 : 签收
    已签收 --> 已完成 : 确认收货
    已签收 --> 退货中 : 申请退货
    退货中 --> 已退款 : 退货成功
    退货中 --> 已签收 : 拒绝退货
    已完成 --> [*]
    已取消 --> [*]
    已退款 --> [*]
```

## 七、项目资源表格

### 7.1 人员配置

| 角色 | 姓名 | 职责 | 工作量 | 备注 |
|------|------|------|--------|------|
| 项目经理 | 张三 | 项目整体管理 | 100% | PMP认证 |
| 技术负责人 | 李四 | 技术架构设计 | 100% | 架构师 |
| 前端开发 | 王五 | 前端开发 | 100% | Vue专家 |
| 后端开发 | 赵六 | 后端开发 | 100% | Java专家 |
| 测试工程师 | 钱七 | 测试工作 | 50% | 兼职 |
| UI设计师 | 孙八 | UI设计 | 50% | 外包 |

### 7.2 技术选型

| 分类 | 技术栈 | 版本 | 用途 |
|------|--------|------|------|
| 前端框架 | Vue.js | 3.3 | 用户界面 |
| UI组件库 | Element Plus | 2.4 | 组件库 |
| 后端框架 | Spring Boot | 3.2 | 服务端 |
| 数据库 | MySQL | 8.0 | 数据存储 |
| 缓存 | Redis | 7.0 | 缓存服务 |
| 消息队列 | RabbitMQ | 3.12 | 异步处理 |
| 容器化 | Docker | 24.0 | 容器部署 |
| CI/CD | Jenkins | 2.426 | 持续集成 |

### 7.3 里程碑计划

| 阶段 | 里程碑 | 完成日期 | 交付物 |
|------|--------|----------|--------|
| M1 | 需求评审通过 | 2024-01-15 | 需求规格说明书 |
| M2 | 设计评审通过 | 2024-01-31 | 设计文档 |
| M3 | 开发完成 | 2024-02-28 | 源代码 |
| M4 | 测试通过 | 2024-03-07 | 测试报告 |
| M5 | 上线发布 | 2024-03-10 | 生产环境 |

## 八、代码示例

### 8.1 前端代码

```javascript
// Vue组件示例
export default {
  name: 'UserProfile',
  data() {
    return {
      user: {
        name: '',
        email: ''
      }
    }
  },
  methods: {
    async fetchUser(id) {
      const response = await fetch(`/api/users/${id}`);
      this.user = await response.json();
    }
  }
}
```

### 8.2 后端代码

```java
// Spring Boot Controller
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        User user = userService.findById(id);
        return ResponseEntity.ok(user);
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User saved = userService.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
```

## 九、数学公式

### 9.1 简单公式

质能方程：$E = mc^2$

### 9.2 复杂公式

二次方程求根公式：

$$x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}$$

### 9.3 矩阵

矩阵表示：

$$
\begin{bmatrix}
a & b \\
c & d
\end{bmatrix}
\begin{bmatrix}
x \\
y
\end{bmatrix}
=
\begin{bmatrix}
ax + by \\
cx + dy
\end{bmatrix}
$$

## 十、总结

本文档演示了：

1. ✅ 流程图（Flowchart）
2. ✅ 甘特图（Gantt Chart）
3. ✅ ER图（Entity Relationship Diagram）
4. ✅ 类图（Class Diagram）
5. ✅ 时序图（Sequence Diagram）
6. ✅ 状态图（State Diagram）
7. ✅ 各类表格
8. ✅ 代码块
9. ✅ 数学公式

---

**文档信息**

| 项目 | 内容 |
|------|------|
| 文档版本 | V1.0 |
| 创建日期 | 2024-01-01 |
| 最后更新 | 2024-01-01 |
| 作者 | 开发团队 |