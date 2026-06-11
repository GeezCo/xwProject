package com.qy.dch.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 报文分类实体类
 * 支持最多5层的树形分类结构
 */
@Data
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分类ID
     */
    private Long id;

    /**
     * 分类名称（全局唯一）
     */
    private String name;

    /**
     * 父节点ID
     */
    private Long parentId;

    /**
     * 层级（1-5）
     */
    private Integer level;

    /**
     * 完整路径（如：根分类/单位A/部门B）
     */
    private String fullPath;

    /**
     * 同级排序
     */
    private Integer sortOrder;

    /**
     * 是否叶子节点（1=是，可挂载报文）
     */
    private Integer isLeaf;

    /**
     * 分类描述
     */
    private String description;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * 子节点列表（用于构建树形结构，不对应数据库字段）
     */
    private List<Category> children;

    /**
     * 报文数量（用于统计，不对应数据库字段）
     */
    private Integer reportCount;

    /**
     * 初始化子节点列表
     */
    public List<Category> getChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
        return children;
    }

    /**
     * 判断是否为叶子节点
     */
    public boolean isLeaf() {
        return isLeaf != null && isLeaf == 1;
    }

    /**
     * 判断是否为根节点
     */
    public boolean isRoot() {
        return parentId == null;
    }

    /**
     * 判断是否可以添加子节点（层级<5）
     */
    public boolean canAddChild() {
        return level != null && level < 5;
    }
}
