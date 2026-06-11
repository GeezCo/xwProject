package com.qy.dch.mapper;

import com.qy.dch.dto.FusionDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 融合报告数据访问接口
 * <p>
 * 提供融合报告表的CRUD操作：
 * - insertFusion：插入新的融合报告
 * - selectFusionById：根据ID查询融合报告
 * - selectFusionList：分页查询融合报告列表
 * - updateFusion：更新融合报告
 * </p>
 */
@Mapper
public interface FusionMapper {

    /**
     * 插入新的融合报告
     *
     * @param fusionDTO 融合报告数据
     * @return 受影响的行数
     */
    int insertFusion(FusionDTO fusionDTO);

    /**
     * 根据ID查询融合报告
     *
     * @param id 融合报告ID
     * @return 融合报告数据
     */
    FusionDTO selectFusionById(Long id);

    /**
     * 分页查询融合报告列表
     *
     * @param offset 偏移量 (pageNum - 1) * pageSize
     * @param limit 每页条数
     * @return 融合报告列表
     */
    List<FusionDTO> selectFusionList(Integer offset, Integer limit);

    /**
     * 查询融合报告总数
     *
     * @return 总条数
     */
    int selectFusionCount();

    /**
     * 更新融合报告
     *
     * @param fusionDTO 融合报告数据
     * @return 受影响的行数
     */
    int updateFusion(FusionDTO fusionDTO);
}