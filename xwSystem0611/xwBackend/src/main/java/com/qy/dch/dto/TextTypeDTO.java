package com.qy.dch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TextTypeDTO {
    private Integer id;
    private String typeName;
    private Integer parentId;
    private List<TextTypeDTO> children;
}
