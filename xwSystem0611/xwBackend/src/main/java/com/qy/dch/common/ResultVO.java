package com.qy.dch.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应封装类
 * <p>
 * 对所有接口的返回结果进行统一封装，包含状态码、数据、消息和标志位，
 * 提供成功和失败的静态工厂方法，便于Controller层快速构建响应。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultVO {

    /** 响应状态码（1：成功，0：失败） */
    private Integer code;

    /** 响应数据（查询结果等） */
    private Object data;

    /** 响应消息描述 */
    private String msg;

    /** 操作标志位（"true"：成功，"false"：失败） */
    private Object flag;

    /**
     * 构建增删改操作的成功响应（无返回数据）
     *
     * @return 成功的ResultVO实例
     */
    public static ResultVO success() {
        return new ResultVO(1, null, "success", "true");
    }

    /**
     * 构建查询操作的成功响应（携带返回数据）
     *
     * @param data 查询结果数据
     * @return 包含数据的成功ResultVO实例
     */
    public static ResultVO success(Object data) {
        return new ResultVO(1, data, "success", "true");
    }

    /**
     * 构建失败响应
     *
     * @param msg 错误消息描述
     * @return 失败的ResultVO实例
     */
    public static ResultVO error(String msg) {
        return new ResultVO(0, null, msg, "false");
    }

}