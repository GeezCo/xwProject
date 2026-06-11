package com.qy.dch.controller;

import com.qy.dch.entity.Category;
import com.qy.dch.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类管理控制器
 * 提供分类的增删改查、树形展示、移动节点等功能
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/category", produces = "application/json; charset=UTF-8")
@CrossOrigin(origins = "*")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 获取完整分类树
     *
     * @return 分类树（树形结构，包含报文数量统计）
     */
    @GetMapping("/tree")
    public ResponseEntity<Map<String, Object>> getCategoryTree() {
        try {
            List<Category> tree = categoryService.getCategoryTree();
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", tree);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取分类树失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", "获取分类树失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 获取所有叶子节点（用于报文归类下拉框）
     *
     * @return 叶子节点列表
     */
    @GetMapping("/leafs")
    public ResponseEntity<Map<String, Object>> getLeafCategories() {
        try {
            List<Category> leafs = categoryService.getLeafCategories();
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", leafs);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取叶子节点失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", "获取叶子节点失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 新增分类节点
     *
     * @param requestBody 请求体，包含 name, parentId, description
     * @return 新增的分类节点
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createCategory(@RequestBody Map<String, Object> requestBody) {
        try {
            String name = (String) requestBody.get("name");
            Long parentId = requestBody.get("parentId") != null
                    ? ((Number) requestBody.get("parentId")).longValue()
                    : null;
            String description = (String) requestBody.get("description");

            if (name == null || name.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("code", 400);
                error.put("message", "分类名称不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            Category newCategory = categoryService.createCategory(name, parentId, description);

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "创建成功");
            result.put("data", newCategory);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("创建分类节点失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 更新分类节点（重命名、修改描述）
     *
     * @param requestBody 请求体，包含 categoryId, newName, newDescription
     * @return 更新后的分类节点
     */
    @PutMapping("/update")
    public ResponseEntity<Map<String, Object>> updateCategory(@RequestBody Map<String, Object> requestBody) {
        try {
            Long categoryId = ((Number) requestBody.get("categoryId")).longValue();
            String newName = (String) requestBody.get("newName");
            String newDescription = (String) requestBody.get("newDescription");

            Category updated = categoryService.updateCategory(categoryId, newName, newDescription);

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "更新成功");
            result.put("data", updated);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("更新分类节点失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 移动分类节点
     *
     * @param requestBody 请求体，包含 categoryId, newParentId
     * @return 移动后的分类节点
     */
    @PostMapping("/move")
    public ResponseEntity<Map<String, Object>> moveCategory(@RequestBody Map<String, Object> requestBody) {
        try {
            Long categoryId = ((Number) requestBody.get("categoryId")).longValue();
            Long newParentId = requestBody.get("newParentId") != null
                    ? ((Number) requestBody.get("newParentId")).longValue()
                    : null;

            Category moved = categoryService.moveCategory(categoryId, newParentId);

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "移动成功");
            result.put("data", moved);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("移动分类节点失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 删除分类节点（级联删除所有子节点）
     *
     * @param categoryId 分类ID
     * @return 删除结果
     */
    @DeleteMapping("/delete/{categoryId}")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable Long categoryId) {
        try {
            categoryService.deleteCategory(categoryId);

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("删除分类节点失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 获取分类节点详情
     *
     * @param categoryId 分类ID
     * @return 分类节点详情
     */
    @GetMapping("/detail/{categoryId}")
    public ResponseEntity<Map<String, Object>> getCategoryDetail(@PathVariable Long categoryId) {
        try {
            Category category = categoryService.getCategoryById(categoryId);
            if (category == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("code", 404);
                error.put("message", "分类节点不存在");
                return ResponseEntity.status(404).body(error);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", category);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取分类详情失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
