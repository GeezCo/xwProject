package com.qy.dch.service.impl;

import com.qy.dch.entity.Category;
import com.qy.dch.mapper.UygurMapper;
import com.qy.dch.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类管理服务实现类
 */
@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private UygurMapper uygurMapper;

    /**
     * 获取完整分类树
     */
    @Override
    public List<Category> getCategoryTree() {
        // 查询所有分类节点
        List<Category> allCategories = uygurMapper.selectAllCategories();

        // 统计每个叶子节点的报文数
        List<Map<String, Object>> reportCounts = uygurMapper.countReportsByCategory();
        Map<Long, Integer> countMap = new HashMap<>();
        for (Map<String, Object> item : reportCounts) {
            Long categoryId = ((Number) item.get("categoryId")).longValue();
            Integer count = ((Number) item.get("reportCount")).intValue();
            countMap.put(categoryId, count);
        }

        // 为每个节点设置报文数量
        for (Category category : allCategories) {
            category.setReportCount(countMap.getOrDefault(category.getId(), 0));
        }

        // 构建树形结构
        return buildTree(allCategories);
    }

    /**
     * 获取所有叶子节点
     */
    @Override
    public List<Category> getLeafCategories() {
        return uygurMapper.selectLeafCategories();
    }

    /**
     * 新增分类节点
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Category createCategory(String name, Long parentId, String description) {
        // 1. 校验名称唯一性
        Category existing = uygurMapper.selectCategoryByName(name);
        if (existing != null) {
            throw new RuntimeException("分类名称已存在: " + name);
        }

        // 2. 获取父节点信息
        Category parent = null;
        if (parentId != null) {
            parent = uygurMapper.selectCategoryById(parentId);
            if (parent == null) {
                throw new RuntimeException("父节点不存在: " + parentId);
            }

            // 校验层级深度（父节点层级必须<5）
            if (parent.getLevel() >= 5) {
                throw new RuntimeException("最多支持5层分类，父节点已达到最大层级");
            }
        }

        // 3. 构建新节点
        Category newCategory = new Category();
        newCategory.setName(name);
        newCategory.setParentId(parentId);
        newCategory.setDescription(description);

        if (parent == null) {
            // 顶级节点
            newCategory.setLevel(1);
            newCategory.setFullPath(name);
            newCategory.setIsLeaf(0);
        } else {
            // 子节点
            newCategory.setLevel(parent.getLevel() + 1);
            newCategory.setFullPath(parent.getFullPath() + "/" + name);
            newCategory.setIsLeaf(1);  // 默认新增节点为叶子节点
        }

        newCategory.setSortOrder(1000);  // 默认排序

        // 4. 插入数据库
        int rows = uygurMapper.insertCategory(newCategory);
        if (rows == 0) {
            throw new RuntimeException("插入分类节点失败");
        }

        log.info("创建分类节点成功: id={}, name={}, fullPath={}",
                 newCategory.getId(), newCategory.getName(), newCategory.getFullPath());

        return newCategory;
    }

    /**
     * 更新分类节点（重命名、修改描述）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Category updateCategory(Long categoryId, String newName, String newDescription) {
        // 1. 查询当前节点
        Category category = uygurMapper.selectCategoryById(categoryId);
        if (category == null) {
            throw new RuntimeException("分类节点不存在: " + categoryId);
        }

        // 2. 如果重命名，校验唯一性
        if (newName != null && !newName.equals(category.getName())) {
            Category existing = uygurMapper.selectCategoryByName(newName);
            if (existing != null) {
                throw new RuntimeException("分类名称已存在: " + newName);
            }

            // 3. 更新名称和路径
            String oldPath = category.getFullPath();
            String newPath = oldPath.substring(0, oldPath.lastIndexOf("/") + 1) + newName;
            if (category.isRoot()) {
                newPath = newName;
            }

            category.setName(newName);
            category.setFullPath(newPath);

            // 4. 更新所有子节点的路径
            uygurMapper.updateChildrenPath(oldPath, newPath);
        }

        // 5. 更新描述
        if (newDescription != null) {
            category.setDescription(newDescription);
        }

        // 6. 保存到数据库
        int rows = uygurMapper.updateCategory(category);
        if (rows == 0) {
            throw new RuntimeException("更新分类节点失败");
        }

        log.info("更新分类节点成功: id={}, name={}, fullPath={}",
                 category.getId(), category.getName(), category.getFullPath());

        return category;
    }

    /**
     * 移动节点（修改父节点）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Category moveCategory(Long categoryId, Long newParentId) {
        // 1. 查询当前节点
        Category category = uygurMapper.selectCategoryById(categoryId);
        if (category == null) {
            throw new RuntimeException("分类节点不存在: " + categoryId);
        }

        // 2. 查询新父节点
        Category newParent = null;
        if (newParentId != null) {
            newParent = uygurMapper.selectCategoryById(newParentId);
            if (newParent == null) {
                throw new RuntimeException("新父节点不存在: " + newParentId);
            }

            // 3. 校验不能移动到自己的子孙节点下（避免循环引用）
            if (newParent.getFullPath().startsWith(category.getFullPath() + "/")) {
                throw new RuntimeException("不能移动到自己的子孙节点下");
            }

            // 4. 校验层级深度
            int newLevel = newParent.getLevel() + 1;
            int maxChildLevel = getMaxChildLevel(category);
            if (newLevel + maxChildLevel - category.getLevel() > 5) {
                throw new RuntimeException("移动后超过最大层级限制（5层）");
            }
        }

        // 5. 计算新路径和层级
        String oldPath = category.getFullPath();
        Integer oldLevel = category.getLevel();
        String newPath;
        int newLevel;

        if (newParent == null) {
            // 移动到根节点
            newPath = category.getName();
            newLevel = 1;
        } else {
            // 移动到指定父节点下
            newPath = newParent.getFullPath() + "/" + category.getName();
            newLevel = newParent.getLevel() + 1;
        }

        // 6. 更新当前节点
        category.setParentId(newParentId);
        category.setLevel(newLevel);
        category.setFullPath(newPath);
        uygurMapper.updateCategory(category);

        // 7. 更新所有子孙节点的路径和层级
        updateDescendantsAfterMove(oldPath, newPath, oldLevel, newLevel);

        log.info("移动分类节点成功: id={}, oldPath={}, newPath={}",
                 categoryId, oldPath, newPath);

        return category;
    }

    /**
     * 删除分类节点（级联删除所有子节点和关联报文）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long categoryId) {
        // 1. 查询节点
        Category category = uygurMapper.selectCategoryById(categoryId);
        if (category == null) {
            throw new RuntimeException("分类节点不存在: " + categoryId);
        }

        // 2. 级联删除所有子孙节点（通过 full_path 前缀匹配）
        int deletedNodes = uygurMapper.deleteCategoryByPathPrefix(category.getFullPath());

        log.info("删除分类节点成功: id={}, name={}, 级联删除 {} 个节点",
                 categoryId, category.getName(), deletedNodes);
    }

    /**
     * 根据 sendUnitName 查找或创建叶子节点
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long findOrCreateLeafBySendUnitName(String sendUnitName) {
        if (sendUnitName == null || sendUnitName.trim().isEmpty()) {
            // 返回"未分类"节点ID
            return 2L;
        }

        // 1. 查找是否存在该名称的分类节点
        Category existing = uygurMapper.selectCategoryByName(sendUnitName);
        if (existing != null) {
            // 只允许使用叶子节点
            if (existing.getIsLeaf() != null && existing.getIsLeaf() == 1) {
                return existing.getId();
            } else {
                throw new RuntimeException(
                    "分类节点 [" + sendUnitName + "] 存在但不是叶子节点，无法导入报文。请先在分类管理中调整该节点为叶子节点或选择其他分类。"
                );
            }
        }

        // 2. 不存在 → 自动创建为"未分类"下的子节点
        Category uncategorized = uygurMapper.selectCategoryById(2L);
        if (uncategorized == null) {
            throw new RuntimeException("系统异常：未分类节点不存在");
        }

        Category newCategory = new Category();
        newCategory.setName(sendUnitName);
        newCategory.setParentId(2L);  // 挂在"未分类"下
        newCategory.setLevel(uncategorized.getLevel() + 1);  // 未分类level+1
        newCategory.setFullPath(uncategorized.getFullPath() + "/" + sendUnitName);
        newCategory.setIsLeaf(1);
        newCategory.setSortOrder(1000);
        newCategory.setDescription("自动创建：来自JSONL导入的未知单位");

        uygurMapper.insertCategory(newCategory);

        // 将"未分类"标记为非叶子节点（因为它现在有子节点了）
        if (uncategorized.getIsLeaf() != null && uncategorized.getIsLeaf() == 1) {
            uncategorized.setIsLeaf(0);
            uygurMapper.updateCategory(uncategorized);
        }

        log.info("自动创建 sendUnitName 分类节点到未分类下: id={}, name={}, fullPath={}",
                 newCategory.getId(), sendUnitName, newCategory.getFullPath());

        return newCategory.getId();
    }

    /**
     * 获取分类节点详情
     */
    @Override
    public Category getCategoryById(Long categoryId) {
        return uygurMapper.selectCategoryById(categoryId);
    }

    // ============================================
    // 私有辅助方法
    // ============================================

    /**
     * 构建树形结构
     */
    private List<Category> buildTree(List<Category> allCategories) {
        // 1. 按 ID 建立索引
        Map<Long, Category> map = new HashMap<>();
        for (Category category : allCategories) {
            map.put(category.getId(), category);
            category.setChildren(new ArrayList<>());
        }

        // 2. 构建父子关系
        List<Category> roots = new ArrayList<>();
        for (Category category : allCategories) {
            if (category.getParentId() == null) {
                // 根节点
                roots.add(category);
            } else {
                // 子节点，添加到父节点的 children 中
                Category parent = map.get(category.getParentId());
                if (parent != null) {
                    parent.getChildren().add(category);
                }
            }
        }

        return roots;
    }

    /**
     * 获取节点的最大子孙层级
     */
    private int getMaxChildLevel(Category category) {
        List<Category> children = uygurMapper.selectByParentId(category.getId());
        if (children == null || children.isEmpty()) {
            return category.getLevel();
        }

        int maxLevel = category.getLevel();
        for (Category child : children) {
            int childMaxLevel = getMaxChildLevel(child);
            if (childMaxLevel > maxLevel) {
                maxLevel = childMaxLevel;
            }
        }
        return maxLevel;
    }

    /**
     * 移动节点后，更新所有子孙节点的路径和层级
     */
    private void updateDescendantsAfterMove(String oldPath, String newPath,
                                             int oldLevel, int newLevel) {
        // 1. 先收集需要更新的子孙节点（在路径更新之前）
        List<Category> descendants = uygurMapper.selectAllCategories()
            .stream()
            .filter(c -> c.getFullPath().startsWith(oldPath + "/"))
            .collect(Collectors.toList());

        // 2. 更新路径
        uygurMapper.updateChildrenPath(oldPath, newPath);

        // 3. 如果层级变化，更新所有子孙节点的层级
        if (oldLevel != newLevel && !descendants.isEmpty()) {
            int levelDiff = newLevel - oldLevel;
            for (Category desc : descendants) {
                desc.setLevel(desc.getLevel() + levelDiff);
                uygurMapper.updateCategory(desc);
            }
            log.info("更新了 {} 个子孙节点的层级，层级差={}", descendants.size(), levelDiff);
        }
    }
}
