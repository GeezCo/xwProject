package com.qy.dch.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qy.dch.dto.AddCategoryResultDTO;
import com.qy.dch.dto.ImportResultDTO;
import com.qy.dch.dto.TextTypeDTO;
import com.qy.dch.dto.OriginTextDTO;
import com.qy.dch.dto.PageResultDTO;
import com.qy.dch.mapper.UygurMapper;
import com.qy.dch.request.GetListRequest;
import com.qy.dch.service.CategoryService;
import com.qy.dch.service.UygurService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 维吾尔语文本管理服务实现类
 * <p>
 * 负责从JSON文件批量导入文本数据到MySQL数据库，
 * 以及提供文本分类查询和文本列表查询功能。
 * </p>
 */
@Slf4j
@Service
public class UygurServiceImpl implements UygurService {

    @Resource
    private UygurMapper uygurMapper;

    @Autowired(required = false)
    private CategoryService categoryService;

    @Autowired(required = false)
    private MinioClient minioClient;

    @Value("${minio.bucket:xianwei-images}")
    private String minioBucket;

    /** JSON数据文件路径，从application.yml的filePath配置项读取 */
    @Value("${filePath}")
    private String filePath;

    /**
     * 从JSON文件批量导入文本数据到数据库
     * 读取配置的JSON文件，解析每条记录的title、content、times字段，
     * 逐条插入到origin_text表中
     */
    @Override
    public void savetext() {

        try {
            // 读取JSON文件全部内容
            String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)));

            JSONArray jsonArray = JSON.parseArray(jsonContent);

            // 逐条解析并插入数据库
            for (int i = 0; i < jsonArray.size(); i++) {
                System.out.println((i + 1) + "/" + jsonArray.size()); // 输出进度
                OriginTextDTO originTextDTO = new OriginTextDTO();
                JSONObject jsonObject = (JSONObject) jsonArray.get(i);
                originTextDTO.setTitle((String) jsonObject.get("title"));
                originTextDTO.setContent((String) jsonObject.get("content"));

                // 将times数组转为逗号分隔的字符串
                JSONArray times = (JSONArray) jsonObject.get("times");
                StringBuilder stringBuilder = new StringBuilder();
                for (int j = 0; j < times.size(); j++) {
                    if (j != 0)
                        stringBuilder.append(",");
                    stringBuilder.append(times.get(j).toString());
                }
                originTextDTO.setTimes(stringBuilder.toString());
                uygurMapper.insertOriginText(originTextDTO);
            }

        } catch (IOException e) {
            System.err.println("文件读取失败：" + e.getMessage());
        } catch (Exception e) {
            System.err.println("JSON解析失败：" + e.getMessage());
        }

    }

    /**
     * 获取所有文本分类
     *
     * @return 文本分类列表，从text_type表查询
     */
    @Override
    public List<TextTypeDTO> getCategory() {
        List<TextTypeDTO> all = uygurMapper.getCategories();
        List<TextTypeDTO> roots = new ArrayList<>();
        for (TextTypeDTO t : all) {
            if (t.getParentId() == null) {
                List<TextTypeDTO> children = new ArrayList<>();
                for (TextTypeDTO c : all) {
                    if (t.getId().equals(c.getParentId())) {
                        children.add(c);
                    }
                }
                t.setChildren(children);
                roots.add(t);
            }
        }
        return roots;
    }

    /**
     * 根据查询条件获取文本列表
     * 支持按分类ID筛选、按报文模态筛选，若未指定则返回全部文本
     *
     * @param getListRequest 查询请求，包含typeId（可选）、modalType（可选）
     * @return 文本列表，按sid排序
     */
    @Override
    public List<OriginTextDTO> getTextList(GetListRequest getListRequest) {
        // 优先按报文模态筛选
        if (getListRequest.getModalType() != null && !getListRequest.getModalType().isEmpty()) {
            log.info("按报文模态筛选: modalType={}", getListRequest.getModalType());
            return uygurMapper.getTextListByModalType(getListRequest.getModalType());
        }
        // 按分类ID筛选
        else if (getListRequest.getTypeId() != null) {
            log.info("按分类ID筛选: typeId={}", getListRequest.getTypeId());
            return uygurMapper.getTextListByType(getListRequest.getTypeId());
        }
        // 返回全部
        else {
            log.info("查询全部文本");
            return uygurMapper.getTextListAll();
        }
    }

    /**
     * 根据查询条件获取文本列表（分页）
     *
     * @param getListRequest 查询请求参数
     * @return 分页结果
     */
    @Override
    public PageResultDTO<OriginTextDTO> getTextListPaged(GetListRequest getListRequest) {
        int pageNum = getListRequest.getPageNum() > 0 ? getListRequest.getPageNum() : 1;
        int pageSize = getListRequest.getPageSize() > 0 ? getListRequest.getPageSize() : 20;
        int offset = (pageNum - 1) * pageSize;

        List<OriginTextDTO> list;
        int total;

        // 收集所有筛选条件
        List<Integer> typeIds = new ArrayList<>();
        List<String> modalTypes = new ArrayList<>();

        // 收集typeIds
        if (getListRequest.getTypeIds() != null && !getListRequest.getTypeIds().isEmpty()) {
            typeIds.addAll(getListRequest.getTypeIds());
        } else if (getListRequest.getTypeId() != null) {
            typeIds.add(getListRequest.getTypeId());
        }

        // 收集modalTypes
        if (getListRequest.getModalTypes() != null && !getListRequest.getModalTypes().isEmpty()) {
            modalTypes.addAll(getListRequest.getModalTypes());
        } else if (getListRequest.getModalType() != null && !getListRequest.getModalType().isEmpty()) {
            modalTypes.add(getListRequest.getModalType());
        }

        // 判断是否有筛选条件
        boolean hasTypeFilter = !typeIds.isEmpty();
        boolean hasModalFilter = !modalTypes.isEmpty();

        // 收集关键词与时间范围
        List<String> keywords = null;
        if (getListRequest.getKeywords() != null && !getListRequest.getKeywords().isEmpty()) {
            keywords = new ArrayList<>();
            for (String kw : getListRequest.getKeywords()) {
                if (kw != null && !kw.trim().isEmpty()) {
                    keywords.add(kw.trim());
                }
            }
            if (keywords.isEmpty()) keywords = null;
        }
        String startTime = (getListRequest.getStartTime() != null && !getListRequest.getStartTime().trim().isEmpty())
                ? getListRequest.getStartTime().trim() : null;
        String endTime = (getListRequest.getEndTime() != null && !getListRequest.getEndTime().trim().isEmpty())
                ? getListRequest.getEndTime().trim() : null;
        boolean hasAdvancedFilter = keywords != null || startTime != null || endTime != null;

        if (hasAdvancedFilter) {
            // 高级筛选：含关键词或时间范围
            log.info("高级筛选（分页）: typeIds={}, modalTypes={}, keywords={}, startTime={}, endTime={}, pageNum={}, pageSize={}",
                    typeIds, modalTypes, keywords, startTime, endTime, pageNum, pageSize);
            list = uygurMapper.getTextListByAdvancedFilterPaged(
                    hasTypeFilter ? typeIds : null,
                    hasModalFilter ? modalTypes : null,
                    keywords,
                    startTime,
                    endTime,
                    offset,
                    pageSize
            );
            total = uygurMapper.countTextListByAdvancedFilter(
                    hasTypeFilter ? typeIds : null,
                    hasModalFilter ? modalTypes : null,
                    keywords,
                    startTime,
                    endTime
            );
        } else if (hasTypeFilter || hasModalFilter) {
            // 使用组合查询
            log.info("组合筛选（分页）: typeIds={}, modalTypes={}, pageNum={}, pageSize={}",
                    typeIds, modalTypes, pageNum, pageSize);
            list = uygurMapper.getTextListByCombinedFilterPaged(
                    hasTypeFilter ? typeIds : null,
                    hasModalFilter ? modalTypes : null,
                    offset,
                    pageSize
            );
            total = uygurMapper.countTextListByCombinedFilter(
                    hasTypeFilter ? typeIds : null,
                    hasModalFilter ? modalTypes : null
            );
        } else {
            // 查询全部
            log.info("查询全部文本（分页）: pageNum={}, pageSize={}", pageNum, pageSize);
            list = uygurMapper.getTextListAllPaged(offset, pageSize);
            total = uygurMapper.countTextListAll();
        }

        return new PageResultDTO<>(list, total, pageNum, pageSize);
    }

    /**
     * 根据ID获取单个报文详情
     *
     * @param sid 报文ID
     * @return 报文详情，若不存在则返回null
     */
    @Override
    public OriginTextDTO getTextById(Long sid) {
        log.info("getTextById: sid={}", sid);
        return uygurMapper.getTextById(sid);
    }

    /**
     * 重置所有报文的抽取状态为未抽取
     *
     * @return 更新的记录数
     */
    @Override
    public int resetAllExtractedStatus() {
        log.info("resetAllExtractedStatus: 重置所有报文抽取状态");
        return uygurMapper.resetAllExtractedStatus();
    }

    /**
     * 获取抽取状态统计信息
     *
     * @return 统计信息Map
     */
    @Override
    public java.util.Map<String, Object> getExtractionStats() {
        return uygurMapper.getExtractionStats();
    }

    /**
     * 新增二级分类（若一级分类不存在则自动创建）
     *
     * @param categoryName 二级分类名称
     * @param parentCategoryName 一级分类名称
     * @return 创建结果
     */
    @Override
    @Transactional
    public AddCategoryResultDTO addCategory(String categoryName, String parentCategoryName) {
        log.info("addCategory: categoryName={}, parentCategoryName={}", categoryName, parentCategoryName);

        AddCategoryResultDTO result = new AddCategoryResultDTO();
        result.setCategoryName(categoryName);
        result.setParentName(parentCategoryName);

        // 1. 查询一级分类是否存在
        TextTypeDTO parentCategory = uygurMapper.getCategoryByNameAndParent(parentCategoryName, null);
        Integer parentId;

        if (parentCategory == null) {
            // 一级分类不存在，创建
            log.info("一级分类不存在，创建: {}", parentCategoryName);
            // TODO: 使用新的 CategoryService 替代
            throw new RuntimeException("请使用 CategoryService.createCategory() 创建分类");
            // uygurMapper.insertCategory(parentCategoryName, null);
            // parentId = uygurMapper.getCategoryIdByName(parentCategoryName);
            // result.setIsNewParent(true);
        } else {
            // 一级分类已存在
            parentId = parentCategory.getId();
            result.setIsNewParent(false);
        }

        result.setParentId(parentId);

        // 2. 检查二级分类是否已存在
        TextTypeDTO existingCategory = uygurMapper.getCategoryByNameAndParent(categoryName, parentId);
        if (existingCategory != null) {
            log.warn("二级分类已存在: {} -> {}, categoryId={}", parentCategoryName, categoryName, existingCategory.getId());
            throw new RuntimeException("二级分类已存在: " + parentCategoryName + " -> " + categoryName);
        }

        // 3. 创建二级分类
        // TODO: 使用新的 CategoryService 替代
        throw new RuntimeException("请使用 CategoryService.createCategory() 创建分类");
        // uygurMapper.insertCategory(categoryName, parentId);
        // Integer categoryId = uygurMapper.getCategoryIdByName(categoryName);
        // result.setCategoryId(categoryId);
        // log.info("分类创建成功: categoryId={}, parentId={}", categoryId, parentId);
        // return result;
    }

    /**
     * 从JSONL文件导入报文
     *
     * @param file JSONL文件
     * @param parentCategoryName 一级分类名称
     * @param categoryName 二级分类名称
     * @return 导入结果
     */
    @Override
    @Transactional
    public ImportResultDTO importFromJsonl(MultipartFile file, Long defaultCategoryId) {
        log.info("importFromJsonl: defaultCategoryId={}, fileName={}",
                defaultCategoryId, file.getOriginalFilename());

        ImportResultDTO result = new ImportResultDTO();
        result.setTotalLines(0);
        result.setSuccessCount(0);
        result.setFailCount(0);

        // 默认分类 ID（sendUnitName 为空时使用）
        Long categoryId = defaultCategoryId != null ? defaultCategoryId : 2L;
        result.setCategoryId(categoryId.intValue());
        log.info("使用默认分类 ID: {}", categoryId);

        // 3. 读取JSONL文件
        List<OriginTextDTO> textList = new ArrayList<>();
        int lineNum = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                result.setTotalLines(lineNum);

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    JSONObject json = JSON.parseObject(line);

                    // 解析字段（支持中英文字段名）
                    String title = json.getString("标题");
                    if (title == null) title = json.getString("title");

                    String content = json.getString("内容");
                    if (content == null) content = json.getString("content");

                    String timeStr = json.getString("时间");
                    if (timeStr == null) timeStr = json.getString("createDate");

                    String sendUnitName = json.getString("sendUnitName");
                    String briefTypeName = json.getString("briefTypeName");

                    if (title == null || content == null || timeStr == null) {
                        result.getErrors().add("第" + lineNum + "行: 缺少必填字段（标题/title, 内容/content, 时间/createDate）");
                        result.setFailCount(result.getFailCount() + 1);
                        continue;
                    }

                    // 转换时间格式（支持多种格式）
                    String mysqlTime = convertTimeFormat(timeStr);

                    // 根据 sendUnitName 查找或创建分类节点
                    Long finalCategoryId;
                    if (sendUnitName != null && !sendUnitName.trim().isEmpty() && categoryService != null) {
                        try {
                            finalCategoryId = categoryService.findOrCreateLeafBySendUnitName(sendUnitName);
                        } catch (Exception e) {
                            log.warn("处理 sendUnitName 失败: {}, 使用默认分类", sendUnitName, e);
                            finalCategoryId = categoryId;
                        }
                    } else {
                        // 使用默认分类ID
                        finalCategoryId = categoryId;
                    }

                    // 构造DTO
                    OriginTextDTO dto = new OriginTextDTO();
                    dto.setTitle(title);
                    dto.setContent(content);
                    dto.setTimes(mysqlTime);
                    dto.setType(finalCategoryId.intValue());
                    dto.setModalType("文字报");
                    dto.setSendUnitName(sendUnitName);
                    dto.setBriefTypeName(briefTypeName);

                    textList.add(dto);
                    result.setSuccessCount(result.getSuccessCount() + 1);

                    // 每100条批量插入一次
                    if (textList.size() >= 100) {
                        uygurMapper.batchInsertTexts(textList);
                        textList.clear();
                    }

                } catch (Exception e) {
                    result.getErrors().add("第" + lineNum + "行解析失败: " + e.getMessage());
                    result.setFailCount(result.getFailCount() + 1);
                }
            }

            // 插入剩余数据
            if (!textList.isEmpty()) {
                uygurMapper.batchInsertTexts(textList);
            }

        } catch (IOException e) {
            result.getErrors().add("文件读取失败: " + e.getMessage());
            log.error("文件读取失败", e);
        }

        log.info("导入完成: 总行数={}, 成功={}, 失败={}", result.getTotalLines(), result.getSuccessCount(), result.getFailCount());
        return result;
    }

    /**
     * 转换时间格式
     * 支持: "2026-01-01"、"2026年01月01日"、"2026-06-04 15:53:44"
     */
    private String convertTimeFormat(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }

        // 如果已经是标准格式（带时分秒），截取日期部分
        if (timeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            return timeStr.substring(0, 10);  // 截取前10位 "2026-06-04"
        }

        // 如果是标准日期格式，直接返回
        if (timeStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return timeStr;
        }

        // 如果是中文格式，转换
        if (timeStr.contains("年")) {
            // "2026年01月01日" -> "2026-01-01"
            try {
                DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDate date = LocalDate.parse(timeStr, inputFormatter);
                return date.format(outputFormatter);
            } catch (Exception e) {
                log.warn("时间格式转换失败: {}", timeStr);
                return timeStr;
            }
        }
        return timeStr;
    }

    /**
     * 删除分类
     *
     * @param categoryId 分类ID
     * @return 是否删除成功
     */
    @Override
    @Transactional
    public boolean deleteCategory(Integer categoryId) {
        log.info("删除分类: categoryId={}", categoryId);
        int rows = uygurMapper.deleteCategory(categoryId);
        return rows > 0;
    }

    /**
     * 批量删除分类
     *
     * @param categoryIds 分类ID列表
     * @return 删除的数量
     */
    @Override
    @Transactional
    public int deleteCategoriesBatch(List<Integer> categoryIds) {
        log.info("批量删除分类: categoryIds={}", categoryIds);
        if (categoryIds == null || categoryIds.isEmpty()) {
            return 0;
        }
        return uygurMapper.deleteCategoriesBatch(categoryIds);
    }

    /**
     * 删除报文
     *
     * @param sid 报文ID
     * @return 是否删除成功
     */
    @Override
    @Transactional
    public boolean deleteText(Long sid) {
        log.info("删除报文: sid={}", sid);
        int rows = uygurMapper.deleteText(sid);
        return rows > 0;
    }

    /**
     * 批量删除报文
     *
     * @param sids 报文ID列表
     * @return 删除的数量
     */
    @Override
    @Transactional
    public int deleteTextsBatch(List<Long> sids) {
        log.info("批量删除报文: sids={}", sids);
        if (sids == null || sids.isEmpty()) {
            return 0;
        }
        return uygurMapper.deleteTextsBatch(sids);
    }

    /**
     * 删除分类及其下的所有报文
     *
     * @param categoryId 分类ID
     * @return 删除的报文数量
     */
    @Override
    @Transactional
    public int deleteCategoryWithTexts(Integer categoryId) {
        log.info("删除分类及其报文: categoryId={}", categoryId);
        // 先删除该分类下的所有报文
        int textCount = uygurMapper.deleteTextsByType(categoryId);
        log.info("删除了{}条报文", textCount);
        // 再删除分类本身
        uygurMapper.deleteCategory(categoryId);
        return textCount;
    }

    /**
     * 批量更新报文的分类
     *
     * @param sids 报文ID列表
     * @param newTypeId 新分类ID
     * @return 更新的数量
     */
    @Override
    @Transactional
    public int updateTextsType(List<Long> sids, Integer newTypeId) {
        log.info("批量更新报文分类: sids={}, newTypeId={}", sids, newTypeId);
        if (sids == null || sids.isEmpty()) {
            return 0;
        }
        return uygurMapper.updateTextsType(sids, newTypeId);
    }

    /**
     * 将指定旧分类ID的所有报文更新为新分类ID
     *
     * @param oldTypeId 旧分类ID
     * @param newTypeId 新分类ID
     * @return 更新的数量
     */
    @Override
    @Transactional
    public int updateTextsByOldType(Integer oldTypeId, Integer newTypeId) {
        log.info("更新旧分类报文: oldTypeId={}, newTypeId={}", oldTypeId, newTypeId);
        return uygurMapper.updateTextsByOldType(oldTypeId, newTypeId);
    }

    /**
     * 新增分类（通用版本，支持一级和二级）
     *
     * @param typeName 分类名称
     * @param parentId 父分类ID，null 表示一级分类
     * @return 新增后的分类（含ID），若分类已存在返回 null
     */
    @Override
    @Transactional
    public TextTypeDTO addCategoryByParentId(String typeName, Integer parentId) {
        log.info("新增分类: typeName={}, parentId={}", typeName, parentId);

        TextTypeDTO existing = uygurMapper.getCategoryByNameAndParent(typeName, parentId);
        if (existing != null) {
            log.warn("分类已存在: typeName={}, parentId={}", typeName, parentId);
            return null;
        }

        // TODO: 使用新的 CategoryService 替代
        throw new RuntimeException("请使用 CategoryService.createCategory() 创建分类");
        // uygurMapper.insertCategory(typeName, parentId);
        // TextTypeDTO newCategory = uygurMapper.getCategoryByNameAndParent(typeName, parentId);
        // log.info("分类创建成功: id={}, typeName={}, parentId={}", newCategory.getId(), typeName, parentId);
        // return newCategory;
    }

    /**
     * 修改分类名称
     *
     * @param categoryId 分类ID
     * @param newTypeName 新分类名称
     * @return 更新的行数
     */
    @Override
    @Transactional
    public int updateCategoryName(Integer categoryId, String newTypeName) {
        log.info("修改分类名称: categoryId={}, newTypeName={}", categoryId, newTypeName);
        return uygurMapper.updateCategoryName(categoryId, newTypeName);
    }

    /**
     * 从JSONL文件导入报文（含图片）
     *
     * @param file JSONL文件
     * @param parentCategoryName 一级分类名称
     * @param categoryName 二级分类名称
     * @param imageFiles 图片文件数组
     * @return 导入结果
     */
    @Override
    @Transactional
    public ImportResultDTO importFromJsonlWithImages(
            MultipartFile file,
            String parentCategoryName,
            String categoryName,
            MultipartFile[] imageFiles) {

        ImportResultDTO result = new ImportResultDTO();

        // 1. 查询分类ID
        TextTypeDTO parentCategory = uygurMapper.getCategoryByNameAndParent(parentCategoryName, null);
        if (parentCategory == null) {
            throw new RuntimeException("一级分类不存在: " + parentCategoryName);
        }
        TextTypeDTO category = uygurMapper.getCategoryByNameAndParent(categoryName, parentCategory.getId());
        if (category == null) {
            throw new RuntimeException("二级分类不存在: " + categoryName);
        }
        Integer categoryId = category.getId();
        result.setCategoryId(categoryId);
        result.setCategoryName(categoryName);

        // __CONTINUE_1__

        // 2. 上传所有图片到 MinIO
        Map<String, String> imagePathMap = new HashMap<>();
        int uploadedImages = 0;

        if (imageFiles != null && imageFiles.length > 0 && minioClient != null) {
            String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            for (MultipartFile imageFile : imageFiles) {
                String originalFilename = imageFile.getOriginalFilename();
                if (originalFilename == null || originalFilename.isEmpty()) continue;

                try {
                    String minioPath = "images/" + dateFolder + "/" + originalFilename;

                    minioClient.putObject(
                        PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(minioPath)
                            .stream(imageFile.getInputStream(), imageFile.getSize(), -1)
                            .contentType(imageFile.getContentType())
                            .build()
                    );

                    imagePathMap.put(originalFilename, minioPath);
                    uploadedImages++;
                    log.info("图片上传成功: {} -> {}", originalFilename, minioPath);

                } catch (Exception e) {
                    log.error("图片上传失败: {}", originalFilename, e);
                    result.getErrors().add("图片上传失败: " + originalFilename);
                }
            }
        }

        // __CONTINUE_2__

        // 3. 解析 JSONL 文件
        List<OriginTextDTO> textList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                result.setTotalLines(lineNum);
                if (line.trim().isEmpty()) continue;

                try {
                    JSONObject json = JSON.parseObject(line);
                    String title = json.getString("标题");
                    String content = json.getString("内容");
                    String timeStr = json.getString("时间");
                    JSONArray imagesArray = json.getJSONArray("图片");

                    String mysqlTime = convertTimeFormat(timeStr);

                    OriginTextDTO dto = new OriginTextDTO();
                    dto.setTitle(title);
                    dto.setContent(content);
                    dto.setTimes(mysqlTime);
                    dto.setType(categoryId);

                    // __CONTINUE_3__

                    // 处理图片字段
                    if (imagesArray != null && !imagesArray.isEmpty()) {
                        dto.setModalType("图文报");

                        List<String> matchedPaths = new ArrayList<>();
                        for (int i = 0; i < imagesArray.size(); i++) {
                            String imgFilename = imagesArray.getString(i);
                            String minioPath = imagePathMap.get(imgFilename);
                            if (minioPath != null) {
                                matchedPaths.add(minioPath);
                            } else {
                                log.warn("图片未找到: {}", imgFilename);
                            }
                        }

                        dto.setImages(JSON.toJSONString(matchedPaths));
                    } else {
                        dto.setModalType("文字报");
                    }

                    textList.add(dto);
                    result.setSuccessCount(result.getSuccessCount() + 1);

                    if (textList.size() >= 100) {
                        uygurMapper.batchInsertTextsWithImages(textList);
                        textList.clear();
                    }

                } catch (Exception e) {
                    result.getErrors().add("第" + lineNum + "行解析失败: " + e.getMessage());
                    result.setFailCount(result.getFailCount() + 1);
                }
            }

            if (!textList.isEmpty()) {
                uygurMapper.batchInsertTextsWithImages(textList);
            }

        } catch (IOException e) {
            result.getErrors().add("文件读取失败: " + e.getMessage());
            log.error("文件读取失败", e);
        }

        result.setUploadedImages(uploadedImages);
        log.info("导入完成: 总行数={}, 成功={}, 失败={}, 上传图片=",
                 result.getTotalLines(), result.getSuccessCount(), result.getFailCount(), uploadedImages);
        return result;
    }
}
