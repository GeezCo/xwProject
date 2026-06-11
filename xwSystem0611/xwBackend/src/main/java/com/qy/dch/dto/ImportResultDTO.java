package com.qy.dch.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResultDTO {
    private Integer totalLines;
    private Integer successCount;
    private Integer failCount;
    private Integer categoryId;
    private String categoryName;
    private Integer uploadedImages = 0;  // 新增：已上传图片数量
    private List<String> errors = new ArrayList<>();
}
