package com.qy.dch.service;

import com.qy.dch.entity.Category;

import java.util.List;

/**
 * 分类管理服务接口
 */
public interface CategoryService {

    /**
     * 获取完整分类树
     *
     * @return 分类树（树形结构）
     */
    List<Category> getCategoryTree();

    /**
     * 获取所有叶子节点
     *
     * @return 叶子节点列表
     */
    List<Category> getLeafCategories();

    /**
     * 新增分类节点
     *
     * @param name 分类名称
     * @param parentId 父节点ID（null表示顶级节点）
     * @param description 描述
     * @return 新增的分类节点
     */
    Category createCategory(String name, Long parentId, String description);

    /**
     * 更新分类节点（重命名、修改描述）
     *
     * @param categoryId 分类ID
     * @param newName 新名称（可为null表示不修改）
     * @param newDescription 新描述（可为null表示不修改）
     * @return 更新后的分类节点
     */
    Category updateCategory(Long categoryId, String newName, String newDescription);

    /**
     * 移动节点（修改父节点）
     *
     * @param categoryId 分类ID
     * @param newParentId 新父节点ID
     * @return 移动后的分类节点
     */
    Category moveCategory(Long categoryId, Long newParentId);

    /**
     * 删除分类节点（级联删除所有子节点）
     *
     * @param categoryId 分类ID
     */
    void deleteCategory(Long categoryId);

    /**
     * 根据 sendUnitName 查找或创建叶子节点
     *
     * @param sendUnitName 发送单位名称
     * @return 分类节点ID
     */
    Long findOrCreateLeafBySendUnitName(String sendUnitName);

    /**
     * 获取分类节点详情
     *
     * @param categoryId 分类ID
     * @return 分类节点
     */
    Category getCategoryById(Long categoryId);
}
