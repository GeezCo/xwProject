# A1 + A2 + A3 三合一实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 后端新增「融合详情 docx/pdf 导出」端点 + 把 Category 相关 SQL 从 UygurMapper 拆为独立 CategoryMapper。

**Architecture:** A3 先行清理 Category 模块边界（新建 CategoryMapper，CategoryServiceImpl 注入切换），再增加共享的 DocumentExportService（POI 5.2.5 生成 .docx / iText 5.5.13.3 生成 .pdf），最后通过 `GET /api/target/fusion/export?targetName=xxx&format=docx|pdf` 暴露给前端。

**Tech Stack:** Spring Boot 2.7.18 + JDK 1.8 + MyBatis-Plus 3.5.5 + Apache POI 5.2.5 + iText 5.5.13.3 + Noto Sans SC（SIL OFL）。

**Spec:** `docs/superpowers/specs/2026-06-15-a1-a2-a3-export-and-category-design.md`

---

## File Structure

| 文件 | 操作 | 责任 |
|------|------|------|
| `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/CategoryMapper.java` | 新建 | 11 个 Category 方法的注解式 Mapper |
| `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/CategoryServiceImpl.java` | 修改 | 注入改为 CategoryMapper |
| `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/UygurMapper.java` | 修改 | 删除 11 个已迁移的 Category 方法 |
| `xwSystem/xwBackend/pom.xml` | 修改 | 新增 itextpdf 5.5.13.3 + itext-asian 5.2.0 |
| `xwSystem/xwBackend/src/main/resources/fonts/NotoSansSC-Regular.ttf` | 新建 | PDF 中文字体（SIL OFL，~5MB） |
| `xwSystem/xwBackend/src/main/java/com/qy/dch/config/PdfFontProvider.java` | 新建 | 启动期加载字体的单例 Bean |
| `xwSystem/xwBackend/src/main/java/com/qy/dch/service/DocumentExportService.java` | 新建 | 接口（exportFusionAsDocx / exportFusionAsPdf） |
| `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DocumentExportServiceImpl.java` | 新建 | POI + iText 双格式实现 |
| `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/TargetController.java` | 修改 | 新增 `GET /fusion/export` 端点 |
| `xwSystem/xwBackend/src/test/java/com/qy/dch/mapper/CategoryMapperTest.java` | 新建 | A3 单元测试 |
| `xwSystem/xwBackend/src/test/java/com/qy/dch/service/DocumentExportServiceTest.java` | 新建 | A2/A1 服务层测试 |
| `xwSystem/xwBackend/src/test/java/com/qy/dch/controller/TargetExportControllerTest.java` | 新建 | A1/A2 Controller MockMvc 测试 |
| `xwSystem/xwFrontend/frontend最新HTML/05-3-target-fusion.html` | 修改 | 替换 exportDocx，新增 exportPdf 按钮 |
| `docs/xwBackend-complete-reference.md` | 修改 | §10.10 新增 `/fusion/export` 行；§17.2 增加导出说明 |

---

## A3 范围说明（重要）

**只迁移基于 `Category` 实体的 11 个方法**（位于 `UygurMapper.java` 第 707-845 行的"新的分类管理方法"区块）：

1. `selectAllCategories()`
2. `selectByParentId(parentId)`
3. `selectCategoryById(id)`
4. `selectCategoryByName(name)`
5. `insertCategory(category)`
6. `updateCategory(category)`
7. `deleteCategoryById(id)`
8. `deleteCategoryByPathPrefix(fullPath)`
9. `updateChildrenPath(oldPathPrefix, newPathPrefix)`
10. `selectLeafCategories()`
11. `countReportsByCategory()`
12. `selectCategoryBySendUnitName(sendUnitName)` — 算 12 个，含 sendUnitName 查询

**保留在 UygurMapper 不动的旧方法**（属于 `text_type` / `origin_text` 字符串操作，与 Category 实体解耦但不在本次迁移范围）：

- `getCategoryByNameAndParent`、`getCategoryIdByName`、`deleteCategory`、`deleteCategoriesBatch`、`countByCategory`、`countBySendUnitInCategory`、`getReportsByCategoryAndSendUnit`、`updateCategoryName` 等。

理由：这些方法被 `UygurServiceImpl` 使用（不是 `CategoryServiceImpl`），与本次 A3 关注的 Category 模块边界无关，搬动会扩大改动范围。

---

## Task 1: 新建 CategoryMapper（注解式接口）

**Files:**
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/CategoryMapper.java`

- [ ] **Step 1.1: 新建 CategoryMapper 文件，复制 UygurMapper 中 12 个 Category 方法**

把 UygurMapper.java 第 707-845 行（"新的分类管理方法"区块）整段方法（含注解、SQL、JavaDoc）复制到新文件，签名保持不变。

完整内容：

```java
package com.qy.dch.mapper;

import com.qy.dch.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 分类管理 Mapper（基于 Category 实体，操作 text_type 表）。
 * <p>
 * 从 UygurMapper 拆出，使 Category 模块自给自足。
 */
@Mapper
public interface CategoryMapper {

    @Select("SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description, " +
            "create_time as createTime, update_time as updateTime " +
            "FROM text_type ORDER BY full_path")
    List<Category> selectAllCategories();

    @Select("<script>" +
            "SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description, " +
            "create_time as createTime, update_time as updateTime " +
            "FROM text_type " +
            "<if test='parentId == null'>WHERE parent_id IS NULL</if>" +
            "<if test='parentId != null'>WHERE parent_id = #{parentId}</if>" +
            "ORDER BY sort_order, id" +
            "</script>")
    List<Category> selectByParentId(@Param("parentId") String parentId);

    @Select("SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description, " +
            "create_time as createTime, update_time as updateTime " +
            "FROM text_type WHERE id = #{id}")
    Category selectCategoryById(String id);

    @Select("SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description " +
            "FROM text_type WHERE name = #{name}")
    Category selectCategoryByName(String name);

    @Insert("INSERT INTO text_type (id, name, parent_id, level, full_path, sort_order, is_leaf, description) " +
            "VALUES (#{id}, #{name}, #{parentId}, #{level}, #{fullPath}, #{sortOrder}, #{isLeaf}, #{description})")
    int insertCategory(Category category);

    @Update("UPDATE text_type SET name = #{name}, parent_id = #{parentId}, level = #{level}, " +
            "full_path = #{fullPath}, sort_order = #{sortOrder}, is_leaf = #{isLeaf}, " +
            "description = #{description} WHERE id = #{id}")
    int updateCategory(Category category);

    @Delete("DELETE FROM text_type WHERE id = #{id}")
    int deleteCategoryById(String id);

    @Delete("DELETE FROM text_type WHERE full_path LIKE CONCAT(#{fullPath}, '%')")
    int deleteCategoryByPathPrefix(String fullPath);

    @Update("UPDATE text_type SET full_path = CONCAT(#{newPathPrefix}, SUBSTRING(full_path, LENGTH(#{oldPathPrefix}) + 1)) " +
            "WHERE full_path LIKE CONCAT(#{oldPathPrefix}, '/%')")
    int updateChildrenPath(@Param("oldPathPrefix") String oldPathPrefix,
                           @Param("newPathPrefix") String newPathPrefix);

    @Select("SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description " +
            "FROM text_type WHERE is_leaf = 1 ORDER BY full_path")
    List<Category> selectLeafCategories();

    @Select("SELECT t.id as categoryId, COUNT(o.id) as reportCount " +
            "FROM text_type t " +
            "LEFT JOIN origin_text o ON t.id = o.type " +
            "WHERE t.is_leaf = 1 " +
            "GROUP BY t.id")
    List<Map<String, Object>> countReportsByCategory();

    @Select("SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description " +
            "FROM text_type WHERE name = #{sendUnitName} AND is_leaf = 1")
    Category selectCategoryBySendUnitName(String sendUnitName);
}
```

- [ ] **Step 1.2: 编译验证**

Run: `cd xwSystem/xwBackend && ./mvnw -DskipTests -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 1.3: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/CategoryMapper.java
git commit -m "feat(category): 新建 CategoryMapper 注解式接口（复制阶段，UygurMapper 暂保留）"
```

---

## Task 2: CategoryServiceImpl 切换注入到 CategoryMapper

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/CategoryServiceImpl.java`

- [ ] **Step 2.1: 修改 import 与字段注入**

`CategoryServiceImpl.java` 第 1-25 行附近：

```java
package com.qy.dch.service.impl;

import com.qy.dch.entity.Category;
import com.qy.dch.mapper.CategoryMapper;       // ← 改这里
import com.qy.dch.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;   // ← 改这里
    // ...
```

把原先 `private final UygurMapper uygurMapper;` 改为 `private final CategoryMapper categoryMapper;`。

- [ ] **Step 2.2: 全文 `uygurMapper.` 替换为 `categoryMapper.`**

整个文件中所有 `uygurMapper.xxx` 调用一律改名（共 11 处调用，分布在 `getCategoryTree`、`getLeafCategories`、`createCategory`、`updateCategory`、`moveCategory`、`deleteCategory`、`findOrCreateLeafBySendUnitName`、`getCategoryById`、`getMaxChildLevel`、`updateDescendantsAfterMove` 等方法中）。

可用 IDE 全文替换或：

```bash
sed -i '' 's/uygurMapper\./categoryMapper./g' \
    xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/CategoryServiceImpl.java
```

替换完成后 grep 检查：

```bash
grep -n "uygurMapper" xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/CategoryServiceImpl.java
```

Expected: 无输出

- [ ] **Step 2.3: 编译验证**

Run: `cd xwSystem/xwBackend && ./mvnw -DskipTests -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 2.4: 启动后端，冒烟 Category 接口**

Run: `cd xwSystem/xwBackend && ./mvnw spring-boot:run`（后台启动，等待启动完成日志）

冒烟：
```bash
curl -s http://localhost:8081/api/category/tree | head -50
curl -s http://localhost:8081/api/category/leafs | head -20
```

Expected: 返回 JSON `{code:0, msg:"success", data: [...]}`，与迁移前一致。

停止：`Ctrl+C`

- [ ] **Step 2.5: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/CategoryServiceImpl.java
git commit -m "refactor(category): CategoryServiceImpl 注入切换为 CategoryMapper"
```

---

## Task 3: 从 UygurMapper 删除已迁移的 12 个 Category 方法

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/UygurMapper.java:705-846`

- [ ] **Step 3.1: 删除"新的分类管理方法"区块**

打开 `UygurMapper.java`，定位到 706 行的注释 `// 新的分类管理方法（基于 Category 实体）`，删除从该行（含 706 行的分隔注释 `// ===...`）到 845 行的右大括号 `}` 之前的全部内容（保留文件最后的 `}` 类闭合括号）。

删除范围：从下面这段开始：

```java
    // ============================================
    // 新的分类管理方法（基于 Category 实体）
    // ============================================
```

到结束于 `selectCategoryBySendUnitName` 方法的 `}`，保留文件最后的类闭合 `}`。

如果 `import com.qy.dch.entity.Category;` 在文件其他地方未被使用，IDE 会标灰；用 `grep -n "Category" xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/UygurMapper.java` 检查是否还有 `Category` 类型的引用：如果没有，删除 import；如果有（如 `text_type` 字符串相关方法签名），保留 import。

- [ ] **Step 3.2: 编译验证**

Run: `cd xwSystem/xwBackend && ./mvnw -DskipTests -o compile`
Expected: BUILD SUCCESS

如果报错（其他类还在调用被删的方法），不要修补——回退本步并扩大 `grep` 检查（参见 Task 1 之前的 caller 调研）。

- [ ] **Step 3.3: 检查 UygurMapper 不再含 Category 实体方法**

```bash
grep -nE "List<Category>|Category select|insertCategory|updateCategory\(Category|deleteCategoryById|deleteCategoryByPathPrefix|updateChildrenPath|countReportsByCategory|selectCategoryBySendUnitName" \
    xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/UygurMapper.java
```

Expected: 无输出（旧字符串方法 `updateCategoryName`、`countByCategory` 等保留是 OK 的，因为它们不操作 `Category` 实体）。

- [ ] **Step 3.4: 启动后端再次冒烟**

```bash
cd xwSystem/xwBackend && ./mvnw spring-boot:run &
sleep 30
curl -s http://localhost:8081/api/category/tree | head -50
curl -s http://localhost:8081/api/category/leafs | head -20
```

Expected: 与 Step 2.4 输出一致。

停止：`kill %1`

- [ ] **Step 3.5: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/mapper/UygurMapper.java
git commit -m "refactor(category): 从 UygurMapper 删除已迁移的 12 个 Category 实体方法"
```

---

## Task 4: CategoryMapperTest 单元测试

**Files:**
- Create: `xwSystem/xwBackend/src/test/java/com/qy/dch/mapper/CategoryMapperTest.java`

- [ ] **Step 4.1: 写测试类**

```java
package com.qy.dch.mapper;

import com.qy.dch.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CategoryMapper 集成测试。
 * <p>
 * 测试方法 @Transactional 后回滚，不污染数据库。
 */
@SpringBootTest
@Transactional
class CategoryMapperTest {

    @Autowired
    private CategoryMapper categoryMapper;

    @Test
    void selectAllCategories_returnsAtLeastOneRoot() {
        List<Category> all = categoryMapper.selectAllCategories();
        assertNotNull(all);
        assertFalse(all.isEmpty(), "至少存在「未分类」占位行");
        assertTrue(all.stream().anyMatch(c -> "未分类".equals(c.getName())));
    }

    @Test
    void selectLeafCategories_allAreLeaves() {
        List<Category> leafs = categoryMapper.selectLeafCategories();
        assertNotNull(leafs);
        leafs.forEach(c ->
            assertEquals(Integer.valueOf(1), c.getIsLeaf(),
                "selectLeafCategories 必须只返回叶子节点")
        );
    }

    @Test
    void countReportsByCategory_returnsList() {
        List<Map<String, Object>> stats = categoryMapper.countReportsByCategory();
        assertNotNull(stats);
        // 每条 Map 含 categoryId 和 reportCount 两个 key
        if (!stats.isEmpty()) {
            Map<String, Object> first = stats.get(0);
            assertTrue(first.containsKey("categoryId"));
            assertTrue(first.containsKey("reportCount"));
        }
    }

    @Test
    void insertSelectUpdateDelete_roundTrip() {
        Category c = new Category();
        String id = "test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        c.setId(id);
        c.setName("UNIT_TEST_NODE_" + id);
        c.setLevel(1);
        c.setFullPath(c.getName());
        c.setSortOrder(9999);
        c.setIsLeaf(0);
        c.setDescription("unit test");

        // insert
        int inserted = categoryMapper.insertCategory(c);
        assertEquals(1, inserted);

        // selectById
        Category fromDb = categoryMapper.selectCategoryById(id);
        assertNotNull(fromDb);
        assertEquals(c.getName(), fromDb.getName());

        // selectByName
        Category byName = categoryMapper.selectCategoryByName(c.getName());
        assertNotNull(byName);
        assertEquals(id, byName.getId());

        // update
        fromDb.setDescription("updated");
        int updated = categoryMapper.updateCategory(fromDb);
        assertEquals(1, updated);

        // delete
        int deleted = categoryMapper.deleteCategoryById(id);
        assertEquals(1, deleted);

        assertNull(categoryMapper.selectCategoryById(id));
    }

    @Test
    void selectByParentId_nullReturnsRoots() {
        List<Category> roots = categoryMapper.selectByParentId(null);
        assertNotNull(roots);
        roots.forEach(c -> assertNull(c.getParentId(),
            "parentId=null 查询必须返回根节点"));
    }

    @Test
    void selectCategoryBySendUnitName_unknownReturnsNull() {
        Category c = categoryMapper.selectCategoryBySendUnitName(
            "__NOT_EXIST_SEND_UNIT_" + UUID.randomUUID());
        assertNull(c);
    }
}
```

- [ ] **Step 4.2: 跑测试**

Run: `cd xwSystem/xwBackend && ./mvnw -o test -Dtest=CategoryMapperTest`
Expected: BUILD SUCCESS，6 个用例全绿。

如果 `@SpringBootTest` 启动期失败，常见原因：测试需要外部 MySQL 在 9204 可用。如果离线开发 MySQL 没启动，先 `cd deploy && docker-compose up -d mysql` 或 `brew services start mysql`。

- [ ] **Step 4.3: Commit**

```bash
git add xwSystem/xwBackend/src/test/java/com/qy/dch/mapper/CategoryMapperTest.java
git commit -m "test(category): 新增 CategoryMapperTest 覆盖 11 个方法的核心路径"
```

---

## Task 5: pom.xml 新增 iText 依赖

**Files:**
- Modify: `xwSystem/xwBackend/pom.xml`

- [ ] **Step 5.1: 添加 iText 5.5.13.3 + itext-asian 5.2.0**

定位到 pom.xml 中 `<dependencies>` 块的末尾（在 `</dependencies>` 关闭标签之前），添加：

```xml
        <!-- PDF 导出：iText 5（AGPL，仅用于离线内部部署） -->
        <dependency>
            <groupId>com.itextpdf</groupId>
            <artifactId>itextpdf</artifactId>
            <version>5.5.13.3</version>
        </dependency>
        <dependency>
            <groupId>com.itextpdf</groupId>
            <artifactId>itext-asian</artifactId>
            <version>5.2.0</version>
        </dependency>
```

> 说明：iText 5.5.13.3 是 AGPL；当前项目为离线内网部署，不对外分发，合规。如需商用授权或避开 AGPL，可后续切换到 OpenPDF（LGPL fork，API 几乎兼容）。

- [ ] **Step 5.2: 拉取依赖**

Run: `cd xwSystem/xwBackend && ./mvnw -o dependency:resolve -DincludeScope=compile`

如果离线仓库无对应包：

```bash
./mvnw dependency:resolve -DincludeScope=compile
```

Expected: 看到 `com.itextpdf:itextpdf:jar:5.5.13.3` 与 `com.itextpdf:itext-asian:jar:5.2.0` 被解析。

- [ ] **Step 5.3: 编译验证**

Run: `cd xwSystem/xwBackend && ./mvnw -DskipTests -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 5.4: Commit**

```bash
git add xwSystem/xwBackend/pom.xml
git commit -m "build(pdf): 引入 iText 5.5.13.3 + itext-asian 5.2.0 依赖"
```

---

## Task 6: 添加 Noto Sans SC 字体资源

**Files:**
- Create: `xwSystem/xwBackend/src/main/resources/fonts/NotoSansSC-Regular.ttf`

- [ ] **Step 6.1: 创建字体目录并下载字体**

```bash
mkdir -p xwSystem/xwBackend/src/main/resources/fonts
cd xwSystem/xwBackend/src/main/resources/fonts

# Noto Sans SC 来自 Google Fonts（SIL OFL 1.1，允许商用与嵌入）
# 离线环境：从内网共享盘 / 已下载好的位置复制即可
# 在线环境可参考：
#   curl -L -o NotoSansSC-Regular.ttf \
#     "https://github.com/notofonts/noto-cjk/raw/main/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf"
# 或 https://fonts.google.com/noto/specimen/Noto+Sans+SC 下载 .ttf 版本
```

如果只有 OTF：iText 也支持 OTF，但本计划坚持使用 TTF 保持一致命名。把文件命名为 `NotoSansSC-Regular.ttf`。

- [ ] **Step 6.2: 校验字体文件**

```bash
ls -lh xwSystem/xwBackend/src/main/resources/fonts/NotoSansSC-Regular.ttf
file xwSystem/xwBackend/src/main/resources/fonts/NotoSansSC-Regular.ttf
```

Expected:
- 大小约 4-10MB（Noto Sans SC 通常 4-7MB）
- `file` 输出包含 `TrueType` 或 `OpenType`

- [ ] **Step 6.3: 确认 .gitignore 不会忽略字体**

```bash
grep -nE "\\.ttf|\\.otf|fonts/" xwSystem/xwBackend/.gitignore 2>/dev/null || echo "no font ignores"
```

Expected: 无字体相关忽略规则（如有，请删除或反向加白）。

- [ ] **Step 6.4: Commit**

```bash
git add xwSystem/xwBackend/src/main/resources/fonts/NotoSansSC-Regular.ttf
git commit -m "chore(pdf): 内嵌 Noto Sans SC 中文字体（SIL OFL 1.1）"
```

---

## Task 7: PdfFontProvider 字体单例 Bean

**Files:**
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/config/PdfFontProvider.java`

- [ ] **Step 7.1: 写 PdfFontProvider 类**

```java
package com.qy.dch.config;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.InputStream;

/**
 * PDF 中文字体单例提供器。
 * <p>
 * 启动期一次性把 Noto Sans SC 字节加载进 iText BaseFont，
 * 后续所有 PDF 导出复用同一个 BaseFont，避免每次请求都解析 TTF。
 * 失败立即抛 IllegalStateException，让 Spring 启动失败而非运行期异常。
 */
@Slf4j
@Component
public class PdfFontProvider {

    private static final String FONT_RESOURCE = "fonts/NotoSansSC-Regular.ttf";

    private BaseFont baseFont;

    @PostConstruct
    public void init() {
        try (InputStream in = new ClassPathResource(FONT_RESOURCE).getInputStream()) {
            byte[] fontBytes = StreamUtils.copyToByteArray(in);
            this.baseFont = BaseFont.createFont(
                    "NotoSansSC-Regular.ttf",
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED,
                    BaseFont.CACHED,
                    fontBytes,
                    null);
            log.info("PDF 中文字体加载成功: {}, bytes={}", FONT_RESOURCE, fontBytes.length);
        } catch (Exception e) {
            throw new IllegalStateException("PDF 字体加载失败: " + FONT_RESOURCE, e);
        }
    }

    /** 正文 11pt 黑色 */
    public Font body() {
        return new Font(baseFont, 11, Font.NORMAL, BaseColor.BLACK);
    }

    /** 一级标题 18pt 加粗 */
    public Font h1() {
        return new Font(baseFont, 18, Font.BOLD, BaseColor.BLACK);
    }

    /** 二级标题 14pt 加粗 */
    public Font h2() {
        return new Font(baseFont, 14, Font.BOLD, BaseColor.BLACK);
    }

    /** 表头 11pt 加粗白字（用于深色表头背景） */
    public Font tableHeader() {
        return new Font(baseFont, 11, Font.BOLD, BaseColor.WHITE);
    }

    /** 小字 9pt 灰色（用于页脚） */
    public Font footer() {
        return new Font(baseFont, 9, Font.NORMAL, BaseColor.GRAY);
    }

    public BaseFont baseFont() {
        return baseFont;
    }
}
```

- [ ] **Step 7.2: 编译验证**

Run: `cd xwSystem/xwBackend && ./mvnw -DskipTests -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 7.3: 启动验证字体加载**

```bash
cd xwSystem/xwBackend && ./mvnw spring-boot:run &
sleep 30
grep "PDF 中文字体加载成功" xwSystem/xwBackend/target/spring.log 2>/dev/null \
    || echo "请人工查看控制台日志"
kill %1
```

Expected: 日志含 `PDF 中文字体加载成功: fonts/NotoSansSC-Regular.ttf, bytes=...`。

如果启动失败 `PDF 字体加载失败`，回头检查 Task 6 字体路径是否正确。

- [ ] **Step 7.4: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/config/PdfFontProvider.java
git commit -m "feat(pdf): 新增 PdfFontProvider 启动期加载 Noto Sans SC 字体单例"
```

---

## Task 8: DocumentExportService 接口

**Files:**
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/DocumentExportService.java`

- [ ] **Step 8.1: 写接口**

```java
package com.qy.dch.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * 文档导出服务（融合详情 → docx / pdf）。
 * <p>
 * 把 {@link TargetFusionService#getFusionDetail(String)} 返回的 Map 渲染为文档，
 * 直接写入下游 {@link OutputStream}（通常是 HTTP ServletOutputStream）。
 */
public interface DocumentExportService {

    /**
     * 导出融合详情为 Word .docx。
     *
     * @param fusionDetail TargetFusionService#getFusionDetail 返回的 Map（不能为 null）
     * @param out          输出流（由调用方负责关闭）
     * @throws IOException IO 异常
     */
    void exportFusionAsDocx(Map<String, Object> fusionDetail, OutputStream out) throws IOException;

    /**
     * 导出融合详情为 PDF。
     *
     * @param fusionDetail TargetFusionService#getFusionDetail 返回的 Map（不能为 null）
     * @param out          输出流（由调用方负责关闭）
     * @throws IOException IO 异常
     */
    void exportFusionAsPdf(Map<String, Object> fusionDetail, OutputStream out) throws IOException;
}
```

- [ ] **Step 8.2: 编译验证**

Run: `cd xwSystem/xwBackend && ./mvnw -DskipTests -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 8.3: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/service/DocumentExportService.java
git commit -m "feat(export): 新增 DocumentExportService 接口（docx + pdf）"
```

---

## Task 9: DocumentExportServiceImpl 骨架 + 图片下载工具

**Files:**
- Create: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DocumentExportServiceImpl.java`

- [ ] **Step 9.1: 写实现骨架（构造注入 + 图片下载 helper）**

```java
package com.qy.dch.service.impl;

import com.qy.dch.config.PdfFontProvider;
import com.qy.dch.service.DocumentExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 融合详情导出实现（Word + PDF 双格式）。
 * <p>
 * 内容布局参考 spec §4 / §5。共用：
 * - 图片下载（RestTemplate, 2s/5s 超时，失败降级为 URL 文本）
 * - basicInfo / fusionResult Map 结构解析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentExportServiceImpl implements DocumentExportService {

    private final PdfFontProvider fontProvider;
    private final RestTemplate imageDownloadRestTemplate;

    // ============================================
    // 公共：图片下载（返回 null 表示失败，调用方降级为文本）
    // ============================================
    byte[] tryDownloadImage(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            ResponseEntity<byte[]> resp = imageDownloadRestTemplate.exchange(
                    url, HttpMethod.GET, HttpEntity.EMPTY, byte[].class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
            log.warn("图片下载失败 status={}, url={}", resp.getStatusCode(), url);
        } catch (Exception e) {
            log.warn("图片下载异常 url={}, err={}", url, e.getMessage());
        }
        return null;
    }

    // ============================================
    // 公共：从 fusionDetail Map 中提取 basicInfo / fusionResult / sources
    // ============================================
    @SuppressWarnings("unchecked")
    static Map<String, Object> basicInfo(Map<String, Object> fusionDetail) {
        Object o = fusionDetail.get("basicInfo");
        return o instanceof Map ? (Map<String, Object>) o : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> fusionResult(Map<String, Object> fusionDetail) {
        Object o = fusionDetail.get("fusionResult");
        return o instanceof Map ? (Map<String, Object>) o : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> sources(Map<String, Object> basicInfo) {
        Object o = basicInfo.get("sources");
        return o instanceof List ? (List<Map<String, Object>>) o : Collections.emptyList();
    }

    static String str(Map<String, Object> map, String key) {
        Object o = map.get(key);
        return o == null ? "" : String.valueOf(o);
    }

    // ============================================
    // 占位：Word / PDF 在 Task 10 / 11 中实现
    // ============================================
    @Override
    public void exportFusionAsDocx(Map<String, Object> fusionDetail, OutputStream out) throws IOException {
        throw new UnsupportedOperationException("Task 10 实现");
    }

    @Override
    public void exportFusionAsPdf(Map<String, Object> fusionDetail, OutputStream out) throws IOException {
        throw new UnsupportedOperationException("Task 11 实现");
    }
}
```

- [ ] **Step 9.2: 新增 RestTemplate Bean（如果项目尚未提供 `imageDownloadRestTemplate`）**

检查是否已存在：

```bash
grep -rn "imageDownloadRestTemplate" xwSystem/xwBackend/src/main/java
```

如果无：

打开 `xwSystem/xwBackend/src/main/java/com/qy/dch/config/`（或全文搜索一个已存在的 `@Configuration` 类），新建/编辑 `WebClientConfig.java`：

```java
package com.qy.dch.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /** 用于 MinIO 图片下载：2s 连接 + 5s 读取超时，避免拖慢导出。 */
    @Bean
    public RestTemplate imageDownloadRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
```

如果项目已有 `WebClientConfig` 或类似配置类，只追加该 Bean 方法即可。

- [ ] **Step 9.3: 编译验证**

Run: `cd xwSystem/xwBackend && ./mvnw -DskipTests -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 9.4: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DocumentExportServiceImpl.java \
        xwSystem/xwBackend/src/main/java/com/qy/dch/config/WebClientConfig.java
git commit -m "feat(export): DocumentExportServiceImpl 骨架 + 图片下载 RestTemplate"
```

---

## Task 10: Word 导出实现（A2）

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DocumentExportServiceImpl.java`

- [ ] **Step 10.1: 增加 POI imports**

在文件顶部 imports 处补充：

```java
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
```

- [ ] **Step 10.2: 替换 `exportFusionAsDocx` 实现**

```java
@Override
public void exportFusionAsDocx(Map<String, Object> fusionDetail, OutputStream out) throws IOException {
    Map<String, Object> basic = basicInfo(fusionDetail);
    Map<String, Object> fr = fusionResult(fusionDetail);
    List<Map<String, Object>> sources = sources(basic);

    try (XWPFDocument doc = new XWPFDocument()) {

        // ---------- 封面 ----------
        XWPFParagraph cover = doc.createParagraph();
        cover.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun coverRun = cover.createRun();
        coverRun.setBold(true);
        coverRun.setFontSize(22);
        coverRun.setText("目标融合分析报告");
        coverRun.addBreak();
        coverRun.addBreak();

        XWPFParagraph subtitle = doc.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun subRun = subtitle.createRun();
        subRun.setFontSize(14);
        subRun.setText("目标名称：" + str(basic, "targetName"));
        subRun.addBreak();
        subRun.setText("生成时间：" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // ---------- §1 基本信息 ----------
        h1Para(doc, "一、基本信息");
        XWPFTable basicTable = doc.createTable(3, 2);
        basicTable.setWidth("100%");
        setRow(basicTable.getRow(0), "目标名称", str(basic, "targetName"));
        setRow(basicTable.getRow(1), "情报来源数", str(basic, "sourceCount"));
        setRow(basicTable.getRow(2), "导出时间",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // ---------- §2 情报来源 ----------
        h1Para(doc, "二、情报来源");
        if (sources.isEmpty()) {
            bodyPara(doc, "（无来源数据）");
        } else {
            XWPFTable srcTable = doc.createTable(sources.size() + 1, 4);
            srcTable.setWidth("100%");
            setHeaderRow(srcTable.getRow(0), "序号", "报文标题", "发送单位", "时间");
            for (int i = 0; i < sources.size(); i++) {
                Map<String, Object> s = sources.get(i);
                setRow(srcTable.getRow(i + 1),
                        String.valueOf(i + 1),
                        str(s, "title"),
                        str(s, "sendUnit"),
                        str(s, "sendTime"));
            }
        }

        // ---------- §3 融合分析 ----------
        h1Para(doc, "三、融合分析结果");

        h2Para(doc, "3.1 综合分析");
        bodyPara(doc, str(fr, "analysis"));

        h2Para(doc, "3.2 信息差异");
        bodyPara(doc, str(fr, "difference"));

        h2Para(doc, "3.3 区域融合结果");
        bodyPara(doc, str(fr, "regionFusionResult"));

        // ---------- §4 报文图片（如有） ----------
        @SuppressWarnings("unchecked")
        List<String> imageUrls = (List<String>) basic.getOrDefault("imageUrls", Collections.emptyList());
        if (!imageUrls.isEmpty()) {
            h1Para(doc, "四、相关图片");
            for (String url : imageUrls) {
                byte[] bytes = tryDownloadImage(url);
                XWPFParagraph imgPara = doc.createParagraph();
                XWPFRun imgRun = imgPara.createRun();
                if (bytes != null) {
                    try (ByteArrayInputStream bin = new ByteArrayInputStream(bytes)) {
                        imgRun.addPicture(bin, XWPFDocument.PICTURE_TYPE_PNG,
                                "img.png", Units.toEMU(400), Units.toEMU(300));
                    } catch (Exception e) {
                        log.warn("图片嵌入 docx 失败 url={}, err={}", url, e.getMessage());
                        imgRun.setText("[图片：" + url + "]");
                    }
                } else {
                    imgRun.setText("[图片：" + url + "]");
                }
            }
        }

        doc.write(out);
    }
}

// ---------- 辅助方法 ----------
private void h1Para(XWPFDocument doc, String text) {
    XWPFParagraph p = doc.createParagraph();
    XWPFRun r = p.createRun();
    r.setBold(true);
    r.setFontSize(16);
    r.setText(text);
}

private void h2Para(XWPFDocument doc, String text) {
    XWPFParagraph p = doc.createParagraph();
    XWPFRun r = p.createRun();
    r.setBold(true);
    r.setFontSize(13);
    r.setText(text);
}

private void bodyPara(XWPFDocument doc, String text) {
    XWPFParagraph p = doc.createParagraph();
    XWPFRun r = p.createRun();
    r.setFontSize(11);
    r.setText(text == null ? "" : text);
}

private void setRow(XWPFTableRow row, String... cells) {
    for (int i = 0; i < cells.length && i < row.getTableCells().size(); i++) {
        XWPFTableCell cell = row.getCell(i);
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun r = p.createRun();
        r.setFontSize(11);
        r.setText(cells[i] == null ? "" : cells[i]);
    }
}

private void setHeaderRow(XWPFTableRow row, String... cells) {
    for (int i = 0; i < cells.length && i < row.getTableCells().size(); i++) {
        XWPFTableCell cell = row.getCell(i);
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(11);
        r.setText(cells[i] == null ? "" : cells[i]);
    }
}
```

- [ ] **Step 10.3: 编译验证**

Run: `cd xwSystem/xwBackend && ./mvnw -DskipTests -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 10.4: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DocumentExportServiceImpl.java
git commit -m "feat(export): 实现 exportFusionAsDocx（POI XWPF 封面+基本信息+来源+融合+图片）"
```

---

## Task 11: PDF 导出实现（A1）

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DocumentExportServiceImpl.java`

- [ ] **Step 11.1: 增加 iText imports**

在文件顶部 imports 处补充：

```java
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
```

- [ ] **Step 11.2: 替换 `exportFusionAsPdf` 实现**

```java
@Override
public void exportFusionAsPdf(Map<String, Object> fusionDetail, OutputStream out) throws IOException {
    Map<String, Object> basic = basicInfo(fusionDetail);
    Map<String, Object> fr = fusionResult(fusionDetail);
    List<Map<String, Object>> sources = sources(basic);

    Document document = new Document(PageSize.A4, 50, 50, 60, 50);
    try {
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new FooterPageEvent(fontProvider));
        document.open();

        // ---------- 封面 ----------
        Paragraph cover = new Paragraph("目标融合分析报告", fontProvider.h1());
        cover.setAlignment(Element.ALIGN_CENTER);
        cover.setSpacingAfter(20);
        document.add(cover);

        Paragraph meta = new Paragraph(
                "目标名称：" + str(basic, "targetName") + "\n" +
                "生成时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                fontProvider.body());
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingAfter(20);
        document.add(meta);

        // ---------- §1 基本信息 ----------
        document.add(new Paragraph("一、基本信息", fontProvider.h2()));
        PdfPTable basicTable = new PdfPTable(2);
        basicTable.setWidthPercentage(100);
        basicTable.setSpacingBefore(8);
        basicTable.setSpacingAfter(12);
        addPdfRow(basicTable, "目标名称", str(basic, "targetName"));
        addPdfRow(basicTable, "情报来源数", str(basic, "sourceCount"));
        addPdfRow(basicTable, "导出时间",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        document.add(basicTable);

        // ---------- §2 情报来源 ----------
        document.add(new Paragraph("二、情报来源", fontProvider.h2()));
        if (sources.isEmpty()) {
            document.add(new Paragraph("（无来源数据）", fontProvider.body()));
        } else {
            PdfPTable srcTable = new PdfPTable(new float[]{1, 5, 3, 3});
            srcTable.setWidthPercentage(100);
            srcTable.setSpacingBefore(8);
            srcTable.setSpacingAfter(12);
            addPdfHeader(srcTable, "序号", "报文标题", "发送单位", "时间");
            for (int i = 0; i < sources.size(); i++) {
                Map<String, Object> s = sources.get(i);
                addPdfBodyCells(srcTable,
                        String.valueOf(i + 1),
                        str(s, "title"),
                        str(s, "sendUnit"),
                        str(s, "sendTime"));
            }
            document.add(srcTable);
        }

        // ---------- §3 融合分析 ----------
        document.add(new Paragraph("三、融合分析结果", fontProvider.h2()));
        addPdfSubSection(document, "3.1 综合分析", str(fr, "analysis"));
        addPdfSubSection(document, "3.2 信息差异", str(fr, "difference"));
        addPdfSubSection(document, "3.3 区域融合结果", str(fr, "regionFusionResult"));

        // ---------- §4 图片 ----------
        @SuppressWarnings("unchecked")
        List<String> imageUrls = (List<String>) basic.getOrDefault("imageUrls", Collections.emptyList());
        if (!imageUrls.isEmpty()) {
            document.add(new Paragraph("四、相关图片", fontProvider.h2()));
            for (String url : imageUrls) {
                byte[] bytes = tryDownloadImage(url);
                if (bytes != null) {
                    try {
                        Image img = Image.getInstance(bytes);
                        img.scaleToFit(400, 300);
                        img.setAlignment(Element.ALIGN_CENTER);
                        document.add(img);
                    } catch (Exception e) {
                        log.warn("图片嵌入 pdf 失败 url={}, err={}", url, e.getMessage());
                        document.add(new Paragraph("[图片：" + url + "]", fontProvider.body()));
                    }
                } else {
                    document.add(new Paragraph("[图片：" + url + "]", fontProvider.body()));
                }
            }
        }

        document.close();
    } catch (DocumentException e) {
        throw new IOException("PDF 生成失败", e);
    }
}

// ---------- PDF 辅助 ----------
private void addPdfRow(PdfPTable table, String label, String value) {
    PdfPCell labelCell = new PdfPCell(new Phrase(label, fontProvider.body()));
    labelCell.setBackgroundColor(new BaseColor(240, 240, 240));
    labelCell.setPadding(6);
    table.addCell(labelCell);
    PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "" : value, fontProvider.body()));
    valueCell.setPadding(6);
    table.addCell(valueCell);
}

private void addPdfHeader(PdfPTable table, String... headers) {
    for (String h : headers) {
        PdfPCell c = new PdfPCell(new Phrase(h, fontProvider.tableHeader()));
        c.setBackgroundColor(new BaseColor(70, 130, 180));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(6);
        table.addCell(c);
    }
}

private void addPdfBodyCells(PdfPTable table, String... cells) {
    for (String s : cells) {
        PdfPCell c = new PdfPCell(new Phrase(s == null ? "" : s, fontProvider.body()));
        c.setPadding(5);
        table.addCell(c);
    }
}

private void addPdfSubSection(Document doc, String title, String body) throws DocumentException {
    Paragraph t = new Paragraph(title, fontProvider.body());
    t.getFont().setStyle("bold");
    t.setSpacingBefore(8);
    doc.add(t);
    Paragraph b = new Paragraph(body == null ? "" : body, fontProvider.body());
    b.setSpacingAfter(8);
    doc.add(b);
}

/** 页脚：居中显示「第 N 页」 */
static class FooterPageEvent extends PdfPageEventHelper {
    private final PdfFontProvider fp;
    FooterPageEvent(PdfFontProvider fp) { this.fp = fp; }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        Rectangle page = document.getPageSize();
        Phrase footer = new Phrase("第 " + writer.getPageNumber() + " 页", fp.footer());
        com.itextpdf.text.pdf.ColumnText.showTextAligned(
                writer.getDirectContent(),
                Element.ALIGN_CENTER,
                footer,
                (page.getLeft() + page.getRight()) / 2,
                page.getBottom() + 25,
                0);
    }
}
```

- [ ] **Step 11.3: 编译验证**

Run: `cd xwSystem/xwBackend && ./mvnw -DskipTests -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 11.4: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/service/impl/DocumentExportServiceImpl.java
git commit -m "feat(export): 实现 exportFusionAsPdf（iText 5 + Noto Sans SC + 页脚分页）"
```

---

## Task 12: TargetController 新增 `/fusion/export` 端点

**Files:**
- Modify: `xwSystem/xwBackend/src/main/java/com/qy/dch/controller/TargetController.java`

- [ ] **Step 12.1: 增加 import + 字段注入**

在 imports 块追加：

```java
import com.qy.dch.common.BusinessException;
import com.qy.dch.common.ErrorCode;
import com.qy.dch.service.DocumentExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
```

在 `private TargetAliasService targetAliasService;` 下追加：

```java
    @Resource
    private DocumentExportService documentExportService;
```

- [ ] **Step 12.2: 新增导出端点**

在 `getFusionDetail` 方法之后追加：

```java
    /**
     * 导出目标融合详情为 docx / pdf。
     *
     * @param targetName 目标名称
     * @param format     "docx" | "pdf"
     * @return 文件流（HttpHeaders 含 Content-Disposition）
     */
    @GetMapping("/fusion/export")
    public ResponseEntity<byte[]> exportFusionReport(
            @RequestParam("targetName") String targetName,
            @RequestParam(value = "format", defaultValue = "docx") String format) {
        log.info("exportFusionReport: targetName={}, format={}", targetName, format);

        if (targetName == null || targetName.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "targetName 不能为空");
        }
        if (!"docx".equalsIgnoreCase(format) && !"pdf".equalsIgnoreCase(format)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "format 必须为 docx 或 pdf");
        }

        Map<String, Object> detail = targetFusionService.getFusionDetail(targetName);
        if (detail == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "目标不存在或无分析记录: " + targetName);
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream(64 * 1024);
        String filename;
        MediaType contentType;
        try {
            if ("pdf".equalsIgnoreCase(format)) {
                documentExportService.exportFusionAsPdf(detail, buf);
                filename = targetName + "_融合报告.pdf";
                contentType = MediaType.APPLICATION_PDF;
            } else {
                documentExportService.exportFusionAsDocx(detail, buf);
                filename = targetName + "_融合报告.docx";
                contentType = MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            }
        } catch (Exception e) {
            log.error("导出失败 targetName={}, format={}", targetName, format, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "导出失败: " + e.getMessage());
        }

        // RFC 5987：兼容中文文件名
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
        headers.setContentLength(buf.size());

        return new ResponseEntity<>(buf.toByteArray(), headers, 200);
    }
```

- [ ] **Step 12.3: 编译验证**

Run: `cd xwSystem/xwBackend && ./mvnw -DskipTests -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 12.4: 启动+冒烟**

```bash
cd xwSystem/xwBackend && ./mvnw spring-boot:run &
sleep 30

# docx
curl -sS -OJ "http://localhost:8081/api/target/fusion/export?targetName=测试目标&format=docx"
# pdf
curl -sS -OJ "http://localhost:8081/api/target/fusion/export?targetName=测试目标&format=pdf"

ls -lh ./*融合报告*
file ./*融合报告.docx ./*融合报告.pdf

kill %1
```

Expected:
- docx 文件大小 > 5KB，`file` 输出 `Microsoft Word 2007+`
- pdf 文件大小 > 10KB，`file` 输出 `PDF document`
- 打开 docx / pdf 中文可见、图片正常或显示 `[图片：URL]`

- [ ] **Step 12.5: Commit**

```bash
git add xwSystem/xwBackend/src/main/java/com/qy/dch/controller/TargetController.java
git commit -m "feat(target): 新增 GET /api/target/fusion/export 支持 docx + pdf 导出"
```

---

## Task 13: DocumentExportServiceTest 服务层测试

**Files:**
- Create: `xwSystem/xwBackend/src/test/java/com/qy/dch/service/DocumentExportServiceTest.java`

- [ ] **Step 13.1: 写测试类**

```java
package com.qy.dch.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DocumentExportServiceTest {

    @Autowired
    private DocumentExportService documentExportService;

    private Map<String, Object> sampleDetail() {
        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("targetName", "样例目标-A");
        basic.put("sourceCount", 2);

        Map<String, Object> src1 = new HashMap<>();
        src1.put("title", "样例报文1");
        src1.put("sendUnit", "样例单位甲");
        src1.put("sendTime", "2026-06-10 09:00");
        Map<String, Object> src2 = new HashMap<>();
        src2.put("title", "样例报文2");
        src2.put("sendUnit", "样例单位乙");
        src2.put("sendTime", "2026-06-12 14:30");
        basic.put("sources", Arrays.asList(src1, src2));
        basic.put("imageUrls", List.<String>of()); // 无图片，避免依赖外网

        Map<String, Object> fr = new LinkedHashMap<>();
        fr.put("analysis", "综合分析示例文本，包含中文字符。");
        fr.put("difference", "差异分析：A 来源称 X，B 来源称 Y。");
        fr.put("regionFusionResult", "区域：北纬30°-32°，东经120°-122°。");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("basicInfo", basic);
        detail.put("fusionResult", fr);
        return detail;
    }

    @Test
    void exportFusionAsDocx_outputsValidZip() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        documentExportService.exportFusionAsDocx(sampleDetail(), out);
        byte[] bytes = out.toByteArray();

        assertTrue(bytes.length > 3_000, "docx 至少 3KB");
        // OOXML 是 ZIP，签名前两字节是 'P' 'K'
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
    }

    @Test
    void exportFusionAsPdf_outputsValidPdfHeader() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        documentExportService.exportFusionAsPdf(sampleDetail(), out);
        byte[] bytes = out.toByteArray();

        assertTrue(bytes.length > 3_000, "pdf 至少 3KB");
        // PDF 文件头 %PDF-
        assertEquals('%', bytes[0]);
        assertEquals('P', bytes[1]);
        assertEquals('D', bytes[2]);
        assertEquals('F', bytes[3]);
    }

    @Test
    void exportFusionAsDocx_emptySourcesHandled() throws Exception {
        Map<String, Object> detail = sampleDetail();
        @SuppressWarnings("unchecked")
        Map<String, Object> basic = (Map<String, Object>) detail.get("basicInfo");
        basic.put("sources", List.of());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 不应抛异常
        documentExportService.exportFusionAsDocx(detail, out);
        assertTrue(out.size() > 1_000);
    }
}
```

- [ ] **Step 13.2: 跑测试**

Run: `cd xwSystem/xwBackend && ./mvnw -o test -Dtest=DocumentExportServiceTest`
Expected: BUILD SUCCESS，3 个用例全绿。

- [ ] **Step 13.3: Commit**

```bash
git add xwSystem/xwBackend/src/test/java/com/qy/dch/service/DocumentExportServiceTest.java
git commit -m "test(export): DocumentExportService 三个用例（docx ZIP 签名 + PDF header + 空来源）"
```

---

## Task 14: TargetExportControllerTest MockMvc 测试

**Files:**
- Create: `xwSystem/xwBackend/src/test/java/com/qy/dch/controller/TargetExportControllerTest.java`

- [ ] **Step 14.1: 写测试类**

```java
package com.qy.dch.controller;

import com.qy.dch.service.TargetFusionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class TargetExportControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @MockBean
    private TargetFusionService targetFusionService;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac).build();
    }

    private Map<String, Object> mockDetail() {
        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("targetName", "T1");
        basic.put("sourceCount", 0);
        basic.put("sources", List.of());
        Map<String, Object> fr = new HashMap<>();
        fr.put("analysis", "A");
        fr.put("difference", "B");
        fr.put("regionFusionResult", "C");
        Map<String, Object> d = new HashMap<>();
        d.put("basicInfo", basic);
        d.put("fusionResult", fr);
        return d;
    }

    @Test
    void exportDocx_returnsOoxmlContentType() throws Exception {
        org.mockito.Mockito.when(targetFusionService.getFusionDetail("T1"))
                .thenReturn(mockDetail());

        MvcResult result = mvc()
                .perform(get("/api/target/fusion/export")
                        .param("targetName", "T1")
                        .param("format", "docx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString(
                            "wordprocessingml.document")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 1_000);
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
    }

    @Test
    void exportPdf_returnsPdfContentType() throws Exception {
        org.mockito.Mockito.when(targetFusionService.getFusionDetail("T1"))
                .thenReturn(mockDetail());

        MvcResult result = mvc()
                .perform(get("/api/target/fusion/export")
                        .param("targetName", "T1")
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 1_000);
        assertEquals('%', bytes[0]);
    }

    @Test
    void exportUnknownTarget_returnsBusinessError() throws Exception {
        org.mockito.Mockito.when(targetFusionService.getFusionDetail("NOPE"))
                .thenReturn(null);

        // GlobalExceptionHandler 把 BusinessException 转 200+ResultVO，所以期望 ResultVO.error
        mvc().perform(get("/api/target/fusion/export")
                        .param("targetName", "NOPE")
                        .param("format", "docx"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(200),
                        org.hamcrest.Matchers.is(400),
                        org.hamcrest.Matchers.is(404)
                )));
    }

    @Test
    void exportInvalidFormat_isRejected() throws Exception {
        org.mockito.Mockito.when(targetFusionService.getFusionDetail("T1"))
                .thenReturn(mockDetail());

        mvc().perform(get("/api/target/fusion/export")
                        .param("targetName", "T1")
                        .param("format", "html"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(200),
                        org.hamcrest.Matchers.is(400)
                )));
    }
}
```

> 说明：项目内 `BusinessException` 由 `GlobalExceptionHandler` 转为 `ResultVO`，HTTP 状态码可能是 200 也可能是 4xx，故用 anyOf 兼容；若需更严格，对照 `GlobalExceptionHandler` 实际返回码硬编码。

- [ ] **Step 14.2: 跑测试**

Run: `cd xwSystem/xwBackend && ./mvnw -o test -Dtest=TargetExportControllerTest`
Expected: BUILD SUCCESS，4 个用例全绿。

- [ ] **Step 14.3: Commit**

```bash
git add xwSystem/xwBackend/src/test/java/com/qy/dch/controller/TargetExportControllerTest.java
git commit -m "test(target): TargetExportControllerTest 覆盖 docx/pdf/未知目标/非法 format"
```

---

## Task 15: 前端 05-3-target-fusion.html 接入导出端点

**Files:**
- Modify: `xwSystem/xwFrontend/frontend最新HTML/05-3-target-fusion.html`

- [ ] **Step 15.1: 定位现有 exportDocx 函数**

```bash
grep -n "exportDocx" "xwSystem/xwFrontend/frontend最新HTML/05-3-target-fusion.html"
```

记下行号（约 845 附近），并查看其前后 30 行上下文。

- [ ] **Step 15.2: 替换为后端真实接口调用 + 新增 exportPdf**

把原 `exportDocx()`（用 Blob 拼 HTML 假装 docx 的实现）整段替换为：

```html
<script>
function exportDocx() {
    const targetName = document.getElementById('fusionTargetName')?.innerText
                     || window.currentTargetName;
    if (!targetName) {
        alert('未确定目标名称');
        return;
    }
    const url = `/api/target/fusion/export?targetName=${encodeURIComponent(targetName)}&format=docx`;
    window.open(url, '_blank');
}

function exportPdf() {
    const targetName = document.getElementById('fusionTargetName')?.innerText
                     || window.currentTargetName;
    if (!targetName) {
        alert('未确定目标名称');
        return;
    }
    const url = `/api/target/fusion/export?targetName=${encodeURIComponent(targetName)}&format=pdf`;
    window.open(url, '_blank');
}
</script>
```

如果原文件中调用 `exportDocx()` 的按钮位于"融合生成目标报"弹窗，请在该按钮旁边新增"导出 PDF"按钮（参照原导出按钮的样式 class）：

```html
<button class="btn btn-primary" onclick="exportDocx()">导出 Word</button>
<button class="btn btn-danger" onclick="exportPdf()">导出 PDF</button>
```

> 注意：`fusionTargetName` 元素 ID 以现有弹窗为准。如果实际 DOM 用的是 `data-target-name` 属性或全局变量 `currentTargetName`，按真实方式取。

- [ ] **Step 15.3: 手工测试**

启动后端：`cd xwSystem/xwBackend && ./mvnw spring-boot:run`

在浏览器打开 `05-3-target-fusion.html`，触发融合（选若干目标）→ 弹窗中点 "导出 Word" / "导出 PDF"，验证浏览器自动下载 `<目标名>_融合报告.docx` / `.pdf`。

打开下载文件确认中文显示正常、内容完整。

- [ ] **Step 15.4: Commit**

```bash
git add "xwSystem/xwFrontend/frontend最新HTML/05-3-target-fusion.html"
git commit -m "feat(frontend): 融合弹窗接入后端 /fusion/export，新增 PDF 导出按钮"
```

---

## Task 16: 更新 docs/xwBackend-complete-reference.md

**Files:**
- Modify: `docs/xwBackend-complete-reference.md`

- [ ] **Step 16.1: §10.10 目标融合接口表追加导出行**

定位到 §10.10「目标融合接口」，在表格末尾新增一行：

```markdown
| GET | `/api/target/fusion/export` | 导出融合详情为 docx/pdf | `targetName`（必填），`format`（docx/pdf，默认 docx） | 文件流（`Content-Type` 对应文档类型，`Content-Disposition: attachment`） |
```

- [ ] **Step 16.2: §17.2（或对应位置）新增导出功能说明**

在「前端业务对接需求 → 05-3-target-fusion」对应章节追加：

```markdown
**导出功能（A1 + A2）**

融合弹窗中提供 Word 与 PDF 两种导出格式：

- 端点：`GET /api/target/fusion/export?targetName=<目标>&format=docx|pdf`
- Word：Apache POI 5.2.5 生成真实 `.docx`（OOXML，可用 Office/WPS 打开）
- PDF：iText 5.5.13.3 + Noto Sans SC 字体，支持中文 + 图片嵌入
- 图片来自 MinIO，下载失败时降级为 `[图片：<URL>]` 文本占位
- 文件名：`<目标名>_融合报告.docx` / `.pdf`，HTTP 头使用 RFC 5987 编码确保中文不乱码

错误处理：
- `targetName` 为空 → `BusinessException(PARAM_INVALID)`
- `format` 非 docx/pdf → `BusinessException(PARAM_INVALID)`
- 目标不存在或无分析记录 → `BusinessException(RESOURCE_NOT_FOUND)`
```

- [ ] **Step 16.3: §11 关于 CategoryMapper 拆分的备注**

在 §11「Mapper 层」或对应 Mapper 索引位置增加：

```markdown
- `CategoryMapper`（基于 `Category` 实体，从 `UygurMapper` 拆出，操作 `text_type` 表）
  - 11 个方法：tree/leaf 查询、增删改、子路径批量更新、按 sendUnit 名查叶子等
```

并标注 `UygurMapper` 中已删除的 Category 实体方法已迁移。

- [ ] **Step 16.4: Commit**

```bash
git add docs/xwBackend-complete-reference.md
git commit -m "docs(reference): 同步 /fusion/export 与 CategoryMapper 拆分"
```

---

## 自检（Self-Review）

执行完所有任务后核对：

- [ ] **Spec 覆盖**：spec §3（A3） → Task 1-4；§4（A2 Word） → Task 5-10；§5（A1 PDF） → Task 5-7, 11；§6（接口规约） → Task 12；§7（前端） → Task 15；§8（测试） → Task 4, 13, 14；§11（实施顺序） → Task 1→16 按依赖序。
- [ ] **类型一致**：`DocumentExportService` 接口签名 (`exportFusionAsDocx` / `exportFusionAsPdf`) 在接口、实现、测试、Controller 调用处一致。
- [ ] **BusinessException 用法**：所有抛出处使用 `new BusinessException(ErrorCode.XXX, "msg")` 双参构造，没有遗漏 ErrorCode。
- [ ] **占位符扫描**：全文搜索 `TBD` / `TODO` / `稍后` / `略` 无残留。
- [ ] **commit 粒度**：每个 Task 末尾都有独立 commit 命令，遵守 "frequent commits"。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-15-a1-a2-a3-export-and-category-plan.md`。

两种执行方式可选，SugarDaddy 你看选哪种？

**1. Subagent-Driven（推荐）** — 每个 Task 派一个全新 subagent 实施，做完两阶段评审（spec 合规 + 代码质量），中间无人工等待。

**2. Inline Execution** — 在本会话里按 Task 顺序执行，关键节点暂停让你 review。

