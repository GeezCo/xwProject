package com.qy.dch.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页数据封装类
 * <p>
 * 用于接口返回分页查询结果，封装分页元数据（页码、页大小、总数、总页数）
 * 和当前页的数据列表。在UygurController中进行内存分页时使用。
 * </p>
 *
 * @author dch
 * @create 2024-07-27 23:11
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageDomain {

	/** 数据总条数 */
	private Integer total;

	/** 当前页的数据列表 */
	private List list;

	/** 当前页码（从1开始） */
	private Integer pageNum;

	/** 每页显示条数 */
	private Integer pageSize;

	/** 总页数 */
	private Integer pages;

}
