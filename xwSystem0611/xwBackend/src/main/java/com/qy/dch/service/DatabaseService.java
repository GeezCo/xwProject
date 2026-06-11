package com.qy.dch.service;

import com.qy.dch.dto.FieldInfoDTO;
import com.qy.dch.request.AddFieldRequest;
import com.qy.dch.request.ModifyFieldRequest;
import com.qy.dch.request.DeleteFieldRequest;

import java.util.List;

/**
 * 数据库管理服务接口
 */
public interface DatabaseService {

    /**
     * 获取表结构信息
     *
     * @param tableName 表名
     * @return 字段信息列表
     */
    List<FieldInfoDTO> getTableStructure(String tableName);

    /**
     * 新增字段
     *
     * @param request 新增字段请求
     */
    void addField(AddFieldRequest request);

    /**
     * 修改字段
     *
     * @param request 修改字段请求
     */
    void modifyField(ModifyFieldRequest request);

    /**
     * 删除字段
     *
     * @param request 删除字段请求
     */
    void deleteField(DeleteFieldRequest request);
}
