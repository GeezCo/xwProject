package com.qy.dch.dto;

import lombok.Data;
import java.util.List;

/**
 * 分页查询结果DTO
 * <p>
 * 封装分页查询的响应数据，包含数据列表和分页信息。
 * </p>
 */
@Data
public class PageResultDTO<T> {
    /** 数据列表 */
    private List<T> list;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private int pageNum;

    /** 每页条数 */
    private int pageSize;

    /** 总页数 */
    private int totalPages;

    public PageResultDTO() {}

    public PageResultDTO(List<T> list, long total, int pageNum, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }
}