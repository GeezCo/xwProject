package com.qy.dch.request;

import lombok.Data;

@Data
public class AddCategoryRequest {
    private String categoryName;
    private String parentCategoryName;
}
