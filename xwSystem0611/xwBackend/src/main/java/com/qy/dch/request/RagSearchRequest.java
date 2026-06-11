package com.qy.dch.request;

import lombok.Data;

@Data
public class RagSearchRequest {
    private String query;
    private Integer topK = 10;
    private Boolean hybrid = true;
}
