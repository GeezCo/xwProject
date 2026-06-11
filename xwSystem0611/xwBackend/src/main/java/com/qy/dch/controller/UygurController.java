package com.qy.dch.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.qy.dch.dto.AddCategoryResultDTO;
import com.qy.dch.dto.ImportResultDTO;
import com.qy.dch.dto.OriginTextDTO;
import com.qy.dch.dto.PageResultDTO;
import com.qy.dch.dto.TextTypeDTO;
import com.qy.dch.common.ResultVO;
import com.qy.dch.domain.PageDomain;
import com.qy.dch.request.AddCategoryRequest;
import com.qy.dch.request.GetListRequest;
import com.qy.dch.service.UygurService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 维吾尔语文本管理控制器
 * <p>
 * 提供以下接口：
 * - GET  /uygur/savetext：从JSON文件批量导入文本数据到数据库
 * - GET  /uygur/category：获取文本分类列表
 * - POST /uygur/getTextList：根据分类和分页参数查询文本列表
 * </p>
 */
@RestController
@RequestMapping("/uygur")
@Slf4j
public class UygurController {

    @Autowired
    UygurService uygurService;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket}")
    private String minioBucket;

    @GetMapping("/config")
    public ResultVO getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("minioPrefix", minioEndpoint + "/" + minioBucket + "/");
        return ResultVO.success(config);
    }

    /**
     * 批量导入文本数据
     * 从配置的JSON文件中读取文本数据，解析后逐条插入数据库
     */
    @GetMapping("/savetext")
    public void keyAccountDoc() {
        log.info("savetext");
        uygurService.savetext();
    }

    /**
     * 获取文本分类列表
     *
     * @return ResultVO 包含所有文本分类信息的列表
     */
    @GetMapping("/category")
    public ResultVO getCategory() {
        log.info("category");
        List<TextTypeDTO> textTypeDTOS = uygurService.getCategory();
        ResultVO resultVO = ResultVO.success(textTypeDTOS);
        return resultVO;
    }

    /**
     * 获取单个报文详情
     * 用于算法服务获取报文内容进行融合处理
     *
     * @param sid 报文ID
     * @return ResultVO 包含报文详情（sid, title, content, times, type）
     */
    @GetMapping("/detail/{sid}")
    public ResultVO getDetail(@PathVariable Long sid) {
        log.info("getDetail: sid={}", sid);
        OriginTextDTO dto = uygurService.getTextById(sid);
        if (dto == null) {
            return ResultVO.error("报文不存在");
        }
        Map<String, Object> item = new HashMap<>();
        item.put("sid", dto.getSid());
        item.put("title", dto.getTitle());
        item.put("content", dto.getContent());
        item.put("times", dto.getTimes());
        item.put("type", dto.getType());
        item.put("modalType", dto.getModalType());
        item.put("isExtracted", dto.getIsExtracted());
        item.put("images", dto.getImages());
        return ResultVO.success(item);
    }

    /**
     * 分页查询文本列表
     * 支持按分类筛选，使用数据库分页查询
     *
     * @param getListRequest 请求参数，包含 pageNum（页码）、pageSize（每页条数）、typeId（分类ID，可选）
     * @return ResultVO 包含分页数据（PageDomain），含总条数、总页数和当前页数据列表
     */
    @PostMapping("/getTextList")
    public ResultVO getList(@RequestBody GetListRequest getListRequest) {
        int pageNum = getListRequest.getPageNum();
        int pageSize = getListRequest.getPageSize();
        log.info("getTextList: pageNum={}, pageSize={}, typeId={}", pageNum, pageSize, getListRequest.getTypeId());

        // 使用数据库分页查询
        PageResultDTO<OriginTextDTO> pageResult = uygurService.getTextListPaged(getListRequest);

        // 构建分页信息
        PageDomain pageDomain = new PageDomain();
        pageDomain.setPages(pageResult.getTotalPages());
        pageDomain.setPageNum(pageNum);
        pageDomain.setPageSize(pageSize);
        pageDomain.setTotal((int) pageResult.getTotal());

        // 转换当前页数据
        List<Map<String, Object>> shows = new ArrayList<>();
        for (OriginTextDTO dto : pageResult.getList()) {
            Map<String, Object> item = new HashMap<>();
            item.put("sid", dto.getSid());
            item.put("title", dto.getTitle());
            item.put("content", dto.getContent());
            item.put("times", dto.getTimes());
            item.put("type", dto.getType());
            item.put("modalType", dto.getModalType());
            item.put("isExtracted", dto.getIsExtracted());
            item.put("images", dto.getImages());
            // 解析labelsJson为labels数组
            if (dto.getLabelsJson() != null && !dto.getLabelsJson().isEmpty()) {
                try {
                    JSONArray labelsArray = JSON.parseArray(dto.getLabelsJson());
                    item.put("labels", labelsArray);
                } catch (Exception e) {
                    item.put("labels", new JSONArray());
                }
            } else {
                item.put("labels", new JSONArray());
            }
            shows.add(item);
        }
        pageDomain.setList(shows);

        return ResultVO.success(pageDomain);
    }

    /**
     * 重置所有报文的抽取状态为未抽取
     *
     * @return ResultVO 包含更新记录数和统计信息
     */
    @PostMapping("/resetExtracted")
    public ResultVO resetExtractedStatus() {
        log.info("resetExtractedStatus: 重置所有报文抽取状态");
        int updated = uygurService.resetAllExtractedStatus();
        Map<String, Object> stats = uygurService.getExtractionStats();
        stats.put("updatedRows", updated);
        return ResultVO.success(stats);
    }

    /**
     * 新增二级分类
     *
     * @param request 请求参数，包含categoryName（二级分类名称）、parentCategoryName（一级分类名称）
     * @return ResultVO 包含创建结果
     */
    @PostMapping("/addCategory")
    public ResultVO addCategory(@RequestBody AddCategoryRequest request) {
        log.info("addCategory: categoryName={}, parentCategoryName={}",
                request.getCategoryName(), request.getParentCategoryName());

        if (request.getCategoryName() == null || request.getCategoryName().isEmpty()) {
            return ResultVO.error("分类名称不能为空");
        }
        if (request.getParentCategoryName() == null || request.getParentCategoryName().isEmpty()) {
            return ResultVO.error("一级分类名称不能为空");
        }

        try {
            AddCategoryResultDTO result = uygurService.addCategory(
                    request.getCategoryName(),
                    request.getParentCategoryName()
            );
            return ResultVO.success(result);
        } catch (Exception e) {
            log.error("新增分类失败", e);
            return ResultVO.error("新增分类失败: " + e.getMessage());
        }
    }

    /**
     * 新增分类（通用版本，支持一级和二级分类）
     * 通过 parentId 区分：null 或 0 为一级分类，其他为二级分类
     *
     * @param request {typeName, parentId}
     * @return ResultVO
     */
    @PostMapping("/category")
    public ResultVO addCategoryGeneral(@RequestBody Map<String, Object> request) {
        String typeName = (String) request.get("typeName");
        Object parentIdObj = request.get("parentId");
        Integer parentId = null;

        if (parentIdObj instanceof Number) {
            int pid = ((Number) parentIdObj).intValue();
            parentId = pid == 0 ? null : pid;
        }

        log.info("addCategoryGeneral: typeName={}, parentId={}", typeName, parentId);

        if (typeName == null || typeName.isEmpty()) {
            return ResultVO.error("分类名称不能为空");
        }

        try {
            TextTypeDTO result = uygurService.addCategoryByParentId(typeName, parentId);
            if (result == null) {
                return ResultVO.error("分类已存在");
            }
            return ResultVO.success(result);
        } catch (Exception e) {
            log.error("新增分类失败", e);
            return ResultVO.error("新增失败: " + e.getMessage());
        }
    }

    /**
     * 修改分类名称
     *
     * @param categoryId 分类ID
     * @param request {newTypeName}
     * @return ResultVO
     */
    @PutMapping("/category/{categoryId}")
    public ResultVO updateCategory(@PathVariable Integer categoryId, @RequestBody Map<String, String> request) {
        log.info("updateCategory: categoryId={}, newTypeName={}", categoryId, request.get("newTypeName"));
        try {
            String newTypeName = request.get("newTypeName");
            if (newTypeName == null || newTypeName.isEmpty()) {
                return ResultVO.error("分类名称不能为空");
            }

            int rows = uygurService.updateCategoryName(categoryId, newTypeName);
            if (rows > 0) {
                return ResultVO.success("修改成功");
            } else {
                return ResultVO.error("分类不存在");
            }
        } catch (Exception e) {
            log.error("修改分类失败", e);
            return ResultVO.error("修改失败: " + e.getMessage());
        }
    }

    /**
     * 从JSONL文件导入报文
     *
     * @param file JSONL文件
     * @param parentCategoryName 一级分类名称
     * @param categoryName 二级分类名称
     * @return ResultVO 包含导入结果
     */
    /**
     * 从JSONL文件批量导入报文
     * 自动根据 JSONL 中的 sendUnitName 字段创建/匹配分类
     *
     * @param file JSONL文件
     * @param defaultCategoryId 默认分类ID（可选，sendUnitName 为空时使用，默认2=未分类）
     * @return ResultVO 包含导入结果
     */
    @PostMapping("/importFromJsonl")
    public ResultVO importFromJsonl(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "defaultCategoryId", required = false, defaultValue = "2") Long defaultCategoryId) {

        log.info("importFromJsonl: defaultCategoryId={}, fileName={}",
                defaultCategoryId, file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResultVO.error("文件不能为空");
        }

        try {
            ImportResultDTO result = uygurService.importFromJsonl(file, defaultCategoryId);

            if (!result.getErrors().isEmpty() && result.getSuccessCount() == 0) {
                return ResultVO.error("导入失败: " + result.getErrors().get(0));
            }

            return ResultVO.success(result);
        } catch (Exception e) {
            log.error("导入失败", e);
            return ResultVO.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 从JSONL文件导入报文（含图片）
     *
     * @param file JSONL文件
     * @param parentCategoryName 一级分类名称
     * @param categoryName 二级分类名称
     * @param images 图片文件数组（可选）
     * @return ResultVO 包含导入结果
     */
    @PostMapping("/importFromJsonlWithImages")
    public ResultVO importFromJsonlWithImages(
            @RequestParam("file") MultipartFile file,
            @RequestParam("parentCategoryName") String parentCategoryName,
            @RequestParam("categoryName") String categoryName,
            @RequestParam(value = "images", required = false) MultipartFile[] images) {

        log.info("importFromJsonlWithImages: parentCategory={}, childCategory={}, fileName={}, imagesCount={}",
                parentCategoryName, categoryName, file.getOriginalFilename(), images != null ? images.length : 0);

        if (file.isEmpty()) {
            return ResultVO.error("文件不能为空");
        }
        if (parentCategoryName == null || parentCategoryName.isEmpty()) {
            return ResultVO.error("一级分类名称不能为空");
        }
        if (categoryName == null || categoryName.isEmpty()) {
            return ResultVO.error("二级分类名称不能为空");
        }

        try {
            ImportResultDTO result = uygurService.importFromJsonlWithImages(
                file, parentCategoryName, categoryName, images);

            if (!result.getErrors().isEmpty() && result.getSuccessCount() == 0) {
                return ResultVO.error("导入失败: " + result.getErrors().get(0));
            }

            return ResultVO.success(result);
        } catch (Exception e) {
            log.error("导入失败", e);
            return ResultVO.error("导入异常: " + e.getMessage());
        }
    }

    /**
     * 删除分类
     *
     * @param categoryId 分类ID
     * @return ResultVO
     */
    @DeleteMapping("/category/{categoryId}")
    public ResultVO deleteCategory(@PathVariable Integer categoryId) {
        log.info("deleteCategory: categoryId={}", categoryId);
        try {
            boolean success = uygurService.deleteCategory(categoryId);
            if (success) {
                return ResultVO.success("删除成功");
            } else {
                return ResultVO.error("删除失败，分类不存在");
            }
        } catch (Exception e) {
            log.error("删除分类失败", e);
            return ResultVO.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除分类
     *
     * @param categoryIds 分类ID列表
     * @return ResultVO
     */
    @PostMapping("/category/batchDelete")
    public ResultVO deleteCategoriesBatch(@RequestBody List<Integer> categoryIds) {
        log.info("deleteCategoriesBatch: categoryIds={}", categoryIds);
        try {
            int count = uygurService.deleteCategoriesBatch(categoryIds);
            return ResultVO.success("成功删除" + count + "个分类");
        } catch (Exception e) {
            log.error("批量删除分类失败", e);
            return ResultVO.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 删除报文
     *
     * @param sid 报文ID
     * @return ResultVO
     */
    @DeleteMapping("/text/{sid}")
    public ResultVO deleteText(@PathVariable Long sid) {
        log.info("deleteText: sid={}", sid);
        try {
            boolean success = uygurService.deleteText(sid);
            if (success) {
                return ResultVO.success("删除成功");
            } else {
                return ResultVO.error("删除失败，报文不存在");
            }
        } catch (Exception e) {
            log.error("删除报文失败", e);
            return ResultVO.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除报文
     *
     * @param sids 报文ID列表
     * @return ResultVO
     */
    @PostMapping("/text/batchDelete")
    public ResultVO deleteTextsBatch(@RequestBody List<Long> sids) {
        log.info("deleteTextsBatch: sids={}", sids);
        try {
            int count = uygurService.deleteTextsBatch(sids);
            return ResultVO.success("成功删除" + count + "条报文");
        } catch (Exception e) {
            log.error("批量删除报文失败", e);
            return ResultVO.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 删除分类及其下的所有报文
     *
     * @param categoryId 分类ID
     * @return ResultVO
     */
    @DeleteMapping("/category/{categoryId}/withTexts")
    public ResultVO deleteCategoryWithTexts(@PathVariable Integer categoryId) {
        log.info("deleteCategoryWithTexts: categoryId={}", categoryId);
        try {
            int textCount = uygurService.deleteCategoryWithTexts(categoryId);
            return ResultVO.success("成功删除分类及其下" + textCount + "条报文");
        } catch (Exception e) {
            log.error("删除分类及报文失败", e);
            return ResultVO.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 按旧分类ID批量更新报文分类
     *
     * @param request 包含oldTypeId和newTypeId的Map
     * @return ResultVO
     */
    @PostMapping("/text/updateByOldType")
    public ResultVO updateTextsByOldType(@RequestBody Map<String, Integer> request) {
        Integer oldTypeId = request.get("oldTypeId");
        Integer newTypeId = request.get("newTypeId");
        log.info("updateTextsByOldType: oldTypeId={}, newTypeId={}", oldTypeId, newTypeId);
        try {
            int count = uygurService.updateTextsByOldType(oldTypeId, newTypeId);
            return ResultVO.success("成功更新" + count + "条报文的分类");
        } catch (Exception e) {
            log.error("更新报文分类失败", e);
            return ResultVO.error("更新失败: " + e.getMessage());
        }
    }

}
