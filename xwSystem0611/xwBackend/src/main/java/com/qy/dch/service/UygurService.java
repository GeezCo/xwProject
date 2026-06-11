package com.qy.dch.service;


import com.qy.dch.dto.AddCategoryResultDTO;
import com.qy.dch.dto.ImportResultDTO;
import com.qy.dch.dto.OriginTextDTO;
import com.qy.dch.dto.PageResultDTO;
import com.qy.dch.dto.TextTypeDTO;
import com.qy.dch.request.GetListRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 维吾尔语文本管理服务接口
 * <p>
 * 定义文本数据导入、分类查询和文本列表查询的业务方法
 * </p>
 */
public interface UygurService {

    /**
     * 批量导入文本数据
     * 从配置的JSON文件中读取文本数据，解析后逐条插入到数据库的origin_text表
     */
    void savetext();

    /**
     * 获取所有文本分类
     *
     * @return 文本分类列表
     */
    List<TextTypeDTO> getCategory();

    /**
     * 根据查询条件获取文本列表
     * 支持按分类ID筛选、按报文模态筛选，若未指定则返回全部文本
     *
     * @param getListRequest 查询请求参数，包含分页信息和可选的分类ID、模态类型
     * @return 符合条件的文本列表
     */
    List<OriginTextDTO> getTextList(GetListRequest getListRequest);

    /**
     * 根据查询条件获取文本列表（分页）
     *
     * @param getListRequest 查询请求参数，包含pageNum、pageSize、typeId、modalType
     * @return 分页结果，包含list、total、pageNum、pageSize、totalPages
     */
    PageResultDTO<OriginTextDTO> getTextListPaged(GetListRequest getListRequest);

    /**
     * 根据ID获取单个报文详情
     *
     * @param sid 报文ID
     * @return 报文详情，若不存在则返回null
     */
    OriginTextDTO getTextById(Long sid);

    /**
     * 重置所有报文的抽取状态为未抽取
     *
     * @return 更新的记录数
     */
    int resetAllExtractedStatus();

    /**
     * 获取抽取状态统计信息
     *
     * @return 统计信息Map
     */
    java.util.Map<String, Object> getExtractionStats();

    /**
     * 新增二级分类（若一级分类不存在则自动创建）
     *
     * @param categoryName 二级分类名称
     * @param parentCategoryName 一级分类名称
     * @return 创建结果
     */
    AddCategoryResultDTO addCategory(String categoryName, String parentCategoryName);

    /**
     * 从JSONL文件导入报文
     *
     * @param file JSONL文件
     * @param parentCategoryName 一级分类名称
     * @param categoryName 二级分类名称
     * @return 导入结果
     */
    ImportResultDTO importFromJsonl(MultipartFile file, Long defaultCategoryId);

    /**
     * 从JSONL文件导入报文（含图片）
     *
     * @param file JSONL文件
     * @param parentCategoryName 一级分类名称
     * @param categoryName 二级分类名称
     * @param imageFiles 图片文件数组
     * @return 导入结果
     */
    ImportResultDTO importFromJsonlWithImages(MultipartFile file, String parentCategoryName, String categoryName, MultipartFile[] imageFiles);

    /**
     * 删除分类
     *
     * @param categoryId 分类ID
     * @return 是否删除成功
     */
    boolean deleteCategory(Integer categoryId);

    /**
     * 批量删除分类
     *
     * @param categoryIds 分类ID列表
     * @return 删除的数量
     */
    int deleteCategoriesBatch(List<Integer> categoryIds);

    /**
     * 删除报文
     *
     * @param sid 报文ID
     * @return 是否删除成功
     */
    boolean deleteText(Long sid);

    /**
     * 批量删除报文
     *
     * @param sids 报文ID列表
     * @return 删除的数量
     */
    int deleteTextsBatch(List<Long> sids);

    /**
     * 删除分类及其下的所有报文
     *
     * @param categoryId 分类ID
     * @return 删除的报文数量
     */
    int deleteCategoryWithTexts(Integer categoryId);

    /**
     * 批量更新报文的分类
     *
     * @param sids 报文ID列表
     * @param newTypeId 新分类ID
     * @return 更新的数量
     */
    int updateTextsType(List<Long> sids, Integer newTypeId);

    /**
     * 将指定旧分类ID的所有报文更新为新分类ID
     *
     * @param oldTypeId 旧分类ID
     * @param newTypeId 新分类ID
     * @return 更新的数量
     */
    int updateTextsByOldType(Integer oldTypeId, Integer newTypeId);

    /**
     * 新增分类（通用版本，支持一级和二级）
     *
     * @param typeName 分类名称
     * @param parentId 父分类ID，null 表示一级分类
     * @return 新增后的分类（含ID），若分类已存在返回 null
     */
    TextTypeDTO addCategoryByParentId(String typeName, Integer parentId);

    /**
     * 修改分类名称
     *
     * @param categoryId 分类ID
     * @param newTypeName 新分类名称
     * @return 更新的行数
     */
    int updateCategoryName(Integer categoryId, String newTypeName);
}
