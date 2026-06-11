package com.qy.dch.dto;

import lombok.Data;

@Data
public class AddCategoryResultDTO {
    private Integer categoryId;
    private String categoryName;
    private Integer parentId;
    private String parentName;
    private Boolean isNewParent;
}
