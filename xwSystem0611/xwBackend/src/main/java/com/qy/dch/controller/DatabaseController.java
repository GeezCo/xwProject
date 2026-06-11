package com.qy.dch.controller;

import com.qy.dch.dto.FieldInfoDTO;
import com.qy.dch.request.AddFieldRequest;
import com.qy.dch.request.ModifyFieldRequest;
import com.qy.dch.request.DeleteFieldRequest;
import com.qy.dch.service.DatabaseService;
import com.qy.dch.common.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据库管理控制器
 * <p>
 * 提供数据库表结构查询、字段增删改功能
 * </p>
 */
@RestController
@RequestMapping("/api/database")
@Slf4j
public class DatabaseController {

    @Autowired
    private DatabaseService databaseService;

    /**
     * 获取表结构
     *
     * @param tableName 表名
     * @return 字段信息列表
     */
    @GetMapping("/table/structure")
    public ResultVO getTableStructure(@RequestParam String tableName) {
        log.info("获取表结构: tableName={}", tableName);
        try {
            List<FieldInfoDTO> fields = databaseService.getTableStructure(tableName);
            return ResultVO.success(fields);
        } catch (Exception e) {
            log.error("获取表结构失败", e);
            return ResultVO.error("获取表结构失败: " + e.getMessage());
        }
    }

    /**
     * 新增字段
     *
     * @param request 新增字段请求
     * @return 操作结果
     */
    @PostMapping("/field/add")
    public ResultVO addField(@RequestBody AddFieldRequest request) {
        log.info("新增字段: table={}, field={}, type={}",
                 request.getTableName(), request.getFieldName(), request.getDataType());
        try {
            databaseService.addField(request);
            return ResultVO.success("字段添加成功");
        } catch (Exception e) {
            log.error("新增字段失败", e);
            return ResultVO.error("新增字段失败: " + e.getMessage());
        }
    }

    /**
     * 修改字段
     *
     * @param request 修改字段请求
     * @return 操作结果
     */
    @PostMapping("/field/modify")
    public ResultVO modifyField(@RequestBody ModifyFieldRequest request) {
        log.info("修改字段: table={}, field={}, type={}",
                 request.getTableName(), request.getFieldName(), request.getDataType());
        try {
            databaseService.modifyField(request);
            return ResultVO.success("字段修改成功");
        } catch (Exception e) {
            log.error("修改字段失败", e);
            return ResultVO.error("修改字段失败: " + e.getMessage());
        }
    }

    /**
     * 删除字段
     *
     * @param request 删除字段请求
     * @return 操作结果
     */
    @PostMapping("/field/delete")
    public ResultVO deleteField(@RequestBody DeleteFieldRequest request) {
        log.info("删除字段: table={}, field={}",
                 request.getTableName(), request.getFieldName());
        try {
            databaseService.deleteField(request);
            return ResultVO.success("字段删除成功");
        } catch (Exception e) {
            log.error("删除字段失败", e);
            return ResultVO.error("删除字段失败: " + e.getMessage());
        }
    }
}
