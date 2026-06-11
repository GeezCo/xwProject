package com.qy.dch.mapper;


import com.qy.dch.dto.TextTypeDTO;
import com.qy.dch.dto.OriginTextDTO;
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
 * 维吾尔语文本数据访问层（MyBatis Mapper）
 * <p>
 * 提供origin_text（原始文本表）和text_type（文本分类表）的数据库操作，
 * 包括文本插入、分类查询、文本列表查询等。
 * </p>
 */
@Mapper
public interface UygurMapper {

    /**
     * 插入一条原始文本记录到origin_text表
     *
     * @param originTextDTO 文本数据，包含title（标题）、content（内容）、times（时间标签）
     */
    @Insert("INSERT INTO `uygur_project`.`origin_text` (`title`, `content`, `times`) " +
            "VALUES (#{title}, #{content}, #{times})")
    void insertOriginText(OriginTextDTO originTextDTO);

    /**
     * 查询所有文本分类
     *
     * @return 分类列表，从text_type表查询
     */
    @Select("select id, name as type_name, parent_id from text_type")
    List<TextTypeDTO> getCategories();

    /**
     * 按分类ID查询文本列表（关联extraction_result获取labels）- 分页
     *
     * @param type 文本分类ID
     * @param offset 偏移量
     * @param pageSize 每页条数
     * @return 该分类下的文本列表，按sid升序排列
     */
    @Select("select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "where o.type = #{type} order by o.sid limit #{pageSize} offset #{offset}")
    List<OriginTextDTO> getTextListByTypePaged(@Param("type") Integer type, @Param("offset") int offset, @Param("pageSize") int pageSize);

    /**
     * 查询指定分类的文本总数
     *
     * @param type 文本分类ID
     * @return 该分类下的总记录数
     */
    @Select("select count(*) from origin_text where type = #{type}")
    int countTextListByType(Integer type);

    /**
     * 按分类ID查询文本列表（关联extraction_result获取labels）
     *
     * @param type 文本分类ID
     * @return 该分类下的文本列表，按sid升序排列
     */
    @Select("select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "where o.type = #{type} order by o.sid")
    List<OriginTextDTO> getTextListByType(Integer type);

    /**
     * 按报文模态类型查询文本列表 - 分页
     *
     * @param modalType 报文模态类型（文字报/图文报/声像报）
     * @param offset 偏移量
     * @param pageSize 每页条数
     * @return 该模态类型下的文本列表，按sid升序排列
     */
    @Select("select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "where o.modal_type = #{modalType} order by o.sid limit #{pageSize} offset #{offset}")
    List<OriginTextDTO> getTextListByModalTypePaged(@Param("modalType") String modalType, @Param("offset") int offset, @Param("pageSize") int pageSize);

    /**
     * 查询指定模态类型的文本总数
     *
     * @param modalType 报文模态类型
     * @return 该模态下的总记录数
     */
    @Select("select count(*) from origin_text where modal_type = #{modalType}")
    int countTextListByModalType(String modalType);

    /**
     * 按报文模态类型查询文本列表
     *
     * @param modalType 报文模态类型（文字报/图文报/声像报）
     * @return 该模态类型下的文本列表，按sid升序排列
     */
    @Select("select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "where o.modal_type = #{modalType} order by o.sid")
    List<OriginTextDTO> getTextListByModalType(String modalType);

    /**
     * 查询全部文本列表（关联extraction_result获取labels）- 分页
     *
     * @param offset 偏移量 (pageNum-1)*pageSize
     * @param pageSize 每页条数
     * @return 文本记录，按sid升序排列
     */
    @Select("select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "order by o.sid limit #{pageSize} offset #{offset}")
    List<OriginTextDTO> getTextListAllPaged(@Param("offset") int offset, @Param("pageSize") int pageSize);

    /**
     * 查询全部文本总数
     *
     * @return 总记录数
     */
    @Select("select count(*) from origin_text")
    int countTextListAll();

    /**
     * 按多个分类ID查询文本列表 - 分页
     *
     * @param typeIds 分类ID列表
     * @param offset 偏移量
     * @param pageSize 每页条数
     * @return 文本列表
     */
    @Select("<script>" +
            "select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "where o.type IN " +
            "<foreach collection='typeIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "order by o.sid limit #{pageSize} offset #{offset}" +
            "</script>")
    List<OriginTextDTO> getTextListByTypeIdsPaged(@Param("typeIds") List<Integer> typeIds, @Param("offset") int offset, @Param("pageSize") int pageSize);

    /**
     * 查询多个分类ID的文本总数
     *
     * @param typeIds 分类ID列表
     * @return 总记录数
     */
    @Select("<script>" +
            "select count(*) from origin_text where type IN " +
            "<foreach collection='typeIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int countTextListByTypeIds(@Param("typeIds") List<Integer> typeIds);

    /**
     * 按多个报文模态类型查询文本列表 - 分页
     *
     * @param modalTypes 报文模态类型列表
     * @param offset 偏移量
     * @param pageSize 每页条数
     * @return 文本列表
     */
    @Select("<script>" +
            "select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "where o.modal_type IN " +
            "<foreach collection='modalTypes' item='type' open='(' separator=',' close=')'>" +
            "#{type}" +
            "</foreach> " +
            "order by o.sid limit #{pageSize} offset #{offset}" +
            "</script>")
    List<OriginTextDTO> getTextListByModalTypesPaged(@Param("modalTypes") List<String> modalTypes, @Param("offset") int offset, @Param("pageSize") int pageSize);

    /**
     * 查询多个报文模态类型的文本总数
     *
     * @param modalTypes 报文模态类型列表
     * @return 总记录数
     */
    @Select("<script>" +
            "select count(*) from origin_text where modal_type IN " +
            "<foreach collection='modalTypes' item='type' open='(' separator=',' close=')'>" +
            "#{type}" +
            "</foreach>" +
            "</script>")
    int countTextListByModalTypes(@Param("modalTypes") List<String> modalTypes);

    /**
     * 按多个分类ID和多个报文模态类型组合查询 - 分页
     *
     * @param typeIds 分类ID列表（可为null）
     * @param modalTypes 报文模态类型列表（可为null）
     * @param offset 偏移量
     * @param pageSize 每页条数
     * @return 文本列表
     */
    @Select("<script>" +
            "select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "where 1=1 " +
            "<if test='typeIds != null and typeIds.size() > 0'>" +
            "AND o.type IN " +
            "<foreach collection='typeIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "</if>" +
            "<if test='modalTypes != null and modalTypes.size() > 0'>" +
            "AND o.modal_type IN " +
            "<foreach collection='modalTypes' item='type' open='(' separator=',' close=')'>" +
            "#{type}" +
            "</foreach> " +
            "</if>" +
            "order by o.sid limit #{pageSize} offset #{offset}" +
            "</script>")
    List<OriginTextDTO> getTextListByCombinedFilterPaged(
            @Param("typeIds") List<Integer> typeIds,
            @Param("modalTypes") List<String> modalTypes,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize);

    /**
     * 查询多个分类ID和多个报文模态类型组合的文本总数
     *
     * @param typeIds 分类ID列表（可为null）
     * @param modalTypes 报文模态类型列表（可为null）
     * @return 总记录数
     */
    @Select("<script>" +
            "select count(*) from origin_text where 1=1 " +
            "<if test='typeIds != null and typeIds.size() > 0'>" +
            "AND type IN " +
            "<foreach collection='typeIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "</if>" +
            "<if test='modalTypes != null and modalTypes.size() > 0'>" +
            "AND modal_type IN " +
            "<foreach collection='modalTypes' item='type' open='(' separator=',' close=')'>" +
            "#{type}" +
            "</foreach> " +
            "</if>" +
            "</script>")
    int countTextListByCombinedFilter(
            @Param("typeIds") List<Integer> typeIds,
            @Param("modalTypes") List<String> modalTypes);

    /**
     * 查询全部文本列表（关联extraction_result获取labels）
     *
     * @return 所有文本记录，按sid升序排列
     */
    @Select("select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "order by o.sid")
    List<OriginTextDTO> getTextListAll();

    /**
     * 根据ID查询单条文本（包含抽取状态）
     *
     * @param sid 文本ID
     * @return 文本DTO
     */
    @Select("select sid, title, content, times, type, modal_type, is_extracted, images from origin_text where sid = #{sid}")
    OriginTextDTO selectById(Long sid);

    /**
     * 检查文本是否已抽取（仅查询is_extracted字段）
     *
     * @param sid 文本ID
     * @return 是否已抽取：null/0-未抽取，1-已抽取
     */
    @Select("select is_extracted from origin_text where sid = #{sid}")
    Integer selectIsExtracted(Long sid);

    /**
     * 根据ID获取单个报文详情（用于融合服务）
     *
     * @param sid 报文ID
     * @return 报文详情，包含sid, title, content, times, type等字段
     */
    @Select("select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "where o.sid = #{sid}")
    OriginTextDTO getTextById(Long sid);

    /**
     * 更新文本的抽取状态
     *
     * @param sid 文本ID
     * @param isExtracted 是否已抽取（0-否，1-是）
     */
    @Update("update origin_text set is_extracted = #{isExtracted} where sid = #{sid}")
    void updateExtractedStatus(@Param("sid") Integer sid, @Param("isExtracted") Integer isExtracted);

    /**
     * 重置所有文本的抽取状态为未抽取
     *
     * @return 更新的记录数
     */
    @Update("update origin_text set is_extracted = 0")
    int resetAllExtractedStatus();

    /**
     * 查询抽取状态统计
     *
     * @return 统计结果Map（total, extracted, not_extracted）
     */
    @Select("SELECT COUNT(*) as total, " +
            "SUM(CASE WHEN is_extracted=1 THEN 1 ELSE 0 END) as extracted, " +
            "SUM(CASE WHEN is_extracted=0 THEN 1 ELSE 0 END) as not_extracted " +
            "FROM origin_text")
    Map<String, Object> getExtractionStats();

    /**
     * 根据分类名称和父ID查询分类（使用sid字段）
     *
     * @param typeName 分类名称
     * @param parentId 父分类ID（可为null）
     * @return 分类DTO
     */
    @Select("<script>" +
            "SELECT id, name as type_name, parent_id FROM text_type " +
            "WHERE name = #{typeName} " +
            "<if test='parentId == null'>AND parent_id IS NULL</if>" +
            "<if test='parentId != null'>AND parent_id = #{parentId}</if>" +
            "</script>")
    TextTypeDTO getCategoryByNameAndParent(@Param("typeName") String typeName, @Param("parentId") Integer parentId);

    /**
     * 根据分类名称查询分类ID（使用id字段）
     *
     * @param categoryName 分类名称
     * @return 分类ID（id）
     */
    @Select("SELECT id FROM text_type WHERE name = #{categoryName} LIMIT 1")
    Integer getCategoryIdByName(String categoryName);

    /**
     * 批量插入报文
     *
     * @param textList 报文列表
     */
    @Insert("<script>" +
            "INSERT INTO origin_text (title, content, times, type, modal_type, sendUnitName, briefTypeName, is_extracted) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.title}, #{item.content}, #{item.times}, #{item.type}, #{item.modalType}, #{item.sendUnitName}, #{item.briefTypeName}, 0)" +
            "</foreach>" +
            "</script>")
    void batchInsertTexts(@Param("list") List<OriginTextDTO> textList);

    /**
     * 批量插入报文（含图片字段）
     *
     * @param textList 报文列表（包含 images 字段）
     */
    @Insert("<script>" +
            "INSERT INTO origin_text (title, content, times, type, modal_type, images, sendUnitName, briefTypeName, is_extracted) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.title}, #{item.content}, #{item.times}, #{item.type}, #{item.modalType}, #{item.images}, #{item.sendUnitName}, #{item.briefTypeName}, 0)" +
            "</foreach>" +
            "</script>")
    void batchInsertTextsWithImages(@Param("list") List<OriginTextDTO> textList);

    /**
     * 删除分类（根据sid）
     *
     * @param categoryId 分类ID（sid）
     * @return 删除的行数
     */
    @Delete("DELETE FROM text_type WHERE sid = #{categoryId}")
    int deleteCategory(Integer categoryId);

    /**
     * 批量删除分类
     *
     * @param categoryIds 分类ID列表
     * @return 删除的行数
     */
    @Delete("<script>" +
            "DELETE FROM text_type WHERE sid IN " +
            "<foreach collection='list' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int deleteCategoriesBatch(@Param("list") List<Integer> categoryIds);

    /**
     * 删除报文（根据sid）
     *
     * @param sid 报文ID
     * @return 删除的行数
     */
    @Delete("DELETE FROM origin_text WHERE sid = #{sid}")
    int deleteText(Long sid);

    /**
     * 批量删除报文
     *
     * @param sids 报文ID列表
     * @return 删除的行数
     */
    @Delete("<script>" +
            "DELETE FROM origin_text WHERE sid IN " +
            "<foreach collection='list' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int deleteTextsBatch(@Param("list") List<Long> sids);

    /**
     * 根据分类ID删除该分类下的所有报文
     *
     * @param typeId 分类ID
     * @return 删除的行数
     */
    @Delete("DELETE FROM origin_text WHERE type = #{typeId}")
    int deleteTextsByType(Integer typeId);

    /**
     * 批量更新报文的分类
     *
     * @param sids 报文ID列表
     * @param newTypeId 新分类ID
     * @return 更新的行数
     */
    @Update("<script>" +
            "UPDATE origin_text SET type = #{newTypeId} WHERE sid IN " +
            "<foreach collection='sids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int updateTextsType(@Param("sids") List<Long> sids, @Param("newTypeId") Integer newTypeId);

    /**
     * 将指定旧分类ID的所有报文更新为新分类ID
     *
     * @param oldTypeId 旧分类ID
     * @param newTypeId 新分类ID
     * @return 更新的行数
     */
    @Update("UPDATE origin_text SET type = #{newTypeId} WHERE type = #{oldTypeId}")
    int updateTextsByOldType(@Param("oldTypeId") Integer oldTypeId, @Param("newTypeId") Integer newTypeId);

    /**
     * 统计各分类下的报文数量
     *
     * @return Map列表，每个Map包含 type 和 cnt 字段
     */
    @Select("SELECT type, COUNT(*) as cnt FROM origin_text GROUP BY type")
    List<Map<String, Object>> countByType();

    /**
     * 统计各模态类型下的报文数量
     *
     * @return Map列表，每个Map包含 modal_type 和 cnt 字段
     */
    @Select("SELECT modal_type, COUNT(*) as cnt FROM origin_text GROUP BY modal_type")
    List<Map<String, Object>> countByModalType();

    /**
     * 更新分类名称
     *
     * @param categoryId 分类ID（sid）
     * @param newTypeName 新分类名称
     * @return 更新的行数
     */
    @Update("UPDATE text_type SET type_name = #{newTypeName} WHERE sid = #{categoryId}")
    int updateCategoryName(@Param("categoryId") Integer categoryId, @Param("newTypeName") String newTypeName);

    /**
     * 按时间段查询报文ID列表
     *
     * @param startDate 开始日期（格式：yyyy-MM-dd）
     * @param endDate 结束日期（格式：yyyy-MM-dd）
     * @param isExtracted 抽取状态过滤（null=全部，0=未抽取，1=已抽取）
     * @return 报文ID列表
     */
    @Select("<script>" +
            "SELECT sid FROM origin_text " +
            "WHERE times BETWEEN #{startDate} AND #{endDate} " +
            "<if test='isExtracted != null'>" +
            "AND is_extracted = #{isExtracted} " +
            "</if>" +
            "ORDER BY sid" +
            "</script>")
    List<Integer> selectIdsByTimeRange(@Param("startDate") String startDate,
                                        @Param("endDate") String endDate,
                                        @Param("isExtracted") Integer isExtracted);

    /**
     * 高级筛选查询：支持分类、模态、关键词（OR）、时间范围组合 - 分页
     *
     * @param typeIds 分类ID列表（可为null）
     * @param modalTypes 报文模态类型列表（可为null）
     * @param keywords 关键词列表（可为null，OR 关系，匹配 title 或 content）
     * @param startTime 开始时间（可为null，格式：yyyy-MM-dd）
     * @param endTime 结束时间（可为null，格式：yyyy-MM-dd）
     * @param offset 偏移量
     * @param pageSize 每页条数
     * @return 文本列表
     */
    @Select("<script>" +
            "select o.*, e.labels_json as labelsJson from origin_text o " +
            "left join extraction_result e on o.sid = e.origin_text_id " +
            "where 1=1 " +
            "<if test='typeIds != null and typeIds.size() > 0'>" +
            "AND o.type IN " +
            "<foreach collection='typeIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "</if>" +
            "<if test='modalTypes != null and modalTypes.size() > 0'>" +
            "AND o.modal_type IN " +
            "<foreach collection='modalTypes' item='mt' open='(' separator=',' close=')'>" +
            "#{mt}" +
            "</foreach> " +
            "</if>" +
            "<if test='keywords != null and keywords.size() > 0'>" +
            "AND (" +
            "<foreach collection='keywords' item='kw' separator=' OR '>" +
            "(o.title LIKE CONCAT('%', #{kw}, '%') OR o.content LIKE CONCAT('%', #{kw}, '%'))" +
            "</foreach>" +
            ") " +
            "</if>" +
            "<if test='startTime != null and startTime != \"\"'>" +
            "AND o.times &gt;= #{startTime} " +
            "</if>" +
            "<if test='endTime != null and endTime != \"\"'>" +
            "AND o.times &lt;= #{endTime} " +
            "</if>" +
            "order by o.sid limit #{pageSize} offset #{offset}" +
            "</script>")
    List<OriginTextDTO> getTextListByAdvancedFilterPaged(
            @Param("typeIds") List<Integer> typeIds,
            @Param("modalTypes") List<String> modalTypes,
            @Param("keywords") List<String> keywords,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize);

    /**
     * 高级筛选查询的总数
     */
    @Select("<script>" +
            "select count(*) from origin_text o where 1=1 " +
            "<if test='typeIds != null and typeIds.size() > 0'>" +
            "AND o.type IN " +
            "<foreach collection='typeIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "</if>" +
            "<if test='modalTypes != null and modalTypes.size() > 0'>" +
            "AND o.modal_type IN " +
            "<foreach collection='modalTypes' item='mt' open='(' separator=',' close=')'>" +
            "#{mt}" +
            "</foreach> " +
            "</if>" +
            "<if test='keywords != null and keywords.size() > 0'>" +
            "AND (" +
            "<foreach collection='keywords' item='kw' separator=' OR '>" +
            "(o.title LIKE CONCAT('%', #{kw}, '%') OR o.content LIKE CONCAT('%', #{kw}, '%'))" +
            "</foreach>" +
            ") " +
            "</if>" +
            "<if test='startTime != null and startTime != \"\"'>" +
            "AND o.times &gt;= #{startTime} " +
            "</if>" +
            "<if test='endTime != null and endTime != \"\"'>" +
            "AND o.times &lt;= #{endTime} " +
            "</if>" +
            "</script>")
    int countTextListByAdvancedFilter(
            @Param("typeIds") List<Integer> typeIds,
            @Param("modalTypes") List<String> modalTypes,
            @Param("keywords") List<String> keywords,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    /**
     * 按时间段查询报文完整信息
     *
     * @param startDate 开始日期（格式：yyyy-MM-dd）
     * @param endDate 结束日期（格式：yyyy-MM-dd）
     * @param isExtracted 抽取状态过滤（null=全部，0=未抽取，1=已抽取）
     * @return 报文列表
     */
    @Select("<script>" +
            "SELECT sid, title, content, times, type, modal_type, is_extracted " +
            "FROM origin_text " +
            "WHERE times BETWEEN #{startDate} AND #{endDate} " +
            "<if test='isExtracted != null'>" +
            "AND is_extracted = #{isExtracted} " +
            "</if>" +
            "ORDER BY times DESC, sid DESC" +
            "</script>")
    List<OriginTextDTO> getReportsByDateRange(@Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("isExtracted") Integer isExtracted);

    // ============================================
    // 新的分类管理方法（基于 Category 实体）
    // ============================================

    /**
     * 查询所有分类节点
     *
     * @return 所有分类节点列表
     */
    @Select("SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description, " +
            "create_time as createTime, update_time as updateTime " +
            "FROM text_type ORDER BY full_path")
    List<Category> selectAllCategories();

    /**
     * 根据父节点ID查询子节点
     *
     * @param parentId 父节点ID（null表示查询根节点）
     * @return 子节点列表
     */
    @Select("<script>" +
            "SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description, " +
            "create_time as createTime, update_time as updateTime " +
            "FROM text_type " +
            "<if test='parentId == null'>WHERE parent_id IS NULL</if>" +
            "<if test='parentId != null'>WHERE parent_id = #{parentId}</if>" +
            "ORDER BY sort_order, id" +
            "</script>")
    List<Category> selectByParentId(@Param("parentId") Long parentId);

    /**
     * 根据ID查询单个分类节点
     *
     * @param id 分类ID
     * @return 分类节点
     */
    @Select("SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description, " +
            "create_time as createTime, update_time as updateTime " +
            "FROM text_type WHERE id = #{id}")
    Category selectCategoryById(Long id);

    /**
     * 根据名称查询分类节点（用于唯一性校验）
     *
     * @param name 分类名称
     * @return 分类节点
     */
    @Select("SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description " +
            "FROM text_type WHERE name = #{name}")
    Category selectCategoryByName(String name);

    /**
     * 插入新分类节点
     *
     * @param category 分类节点
     * @return 插入的行数
     */
    @Insert("INSERT INTO text_type (name, parent_id, level, full_path, sort_order, is_leaf, description) " +
            "VALUES (#{name}, #{parentId}, #{level}, #{fullPath}, #{sortOrder}, #{isLeaf}, #{description})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCategory(Category category);

    /**
     * 更新分类节点
     *
     * @param category 分类节点
     * @return 更新的行数
     */
    @Update("UPDATE text_type SET name = #{name}, parent_id = #{parentId}, level = #{level}, " +
            "full_path = #{fullPath}, sort_order = #{sortOrder}, is_leaf = #{isLeaf}, " +
            "description = #{description} WHERE id = #{id}")
    int updateCategory(Category category);

    /**
     * 删除分类节点（级联删除子节点）
     *
     * @param id 分类ID
     * @return 删除的行数
     */
    @Delete("DELETE FROM text_type WHERE id = #{id}")
    int deleteCategoryById(Long id);

    /**
     * 批量删除分类节点（根据 full_path 前缀匹配删除所有子孙节点）
     *
     * @param fullPath 完整路径前缀
     * @return 删除的行数
     */
    @Delete("DELETE FROM text_type WHERE full_path LIKE CONCAT(#{fullPath}, '%')")
    int deleteCategoryByPathPrefix(String fullPath);

    /**
     * 更新子节点的路径（重命名或移动节点时使用）
     *
     * @param oldPathPrefix 旧路径前缀
     * @param newPathPrefix 新路径前缀
     * @return 更新的行数
     */
    @Update("UPDATE text_type SET full_path = CONCAT(#{newPathPrefix}, SUBSTRING(full_path, LENGTH(#{oldPathPrefix}) + 1)) " +
            "WHERE full_path LIKE CONCAT(#{oldPathPrefix}, '/%')")
    int updateChildrenPath(@Param("oldPathPrefix") String oldPathPrefix,
                           @Param("newPathPrefix") String newPathPrefix);

    /**
     * 查询所有叶子节点
     *
     * @return 叶子节点列表
     */
    @Select("SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description " +
            "FROM text_type WHERE is_leaf = 1 ORDER BY full_path")
    List<Category> selectLeafCategories();

    /**
     * 统计每个叶子节点关联的报文数
     *
     * @return Map列表，key为categoryId，value为reportCount
     */
    @Select("SELECT t.id as categoryId, COUNT(o.sid) as reportCount " +
            "FROM text_type t " +
            "LEFT JOIN origin_text o ON t.id = o.type " +
            "WHERE t.is_leaf = 1 " +
            "GROUP BY t.id")
    List<Map<String, Object>> countReportsByCategory();

    /**
     * 根据 sendUnitName 查询对应的分类节点
     *
     * @param sendUnitName 发送单位名称
     * @return 分类节点（叶子节点）
     */
    @Select("SELECT id, name, parent_id as parentId, level, full_path as fullPath, " +
            "sort_order as sortOrder, is_leaf as isLeaf, description " +
            "FROM text_type WHERE name = #{sendUnitName} AND is_leaf = 1")
    Category selectCategoryBySendUnitName(String sendUnitName);

}

