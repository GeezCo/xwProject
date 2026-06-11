package com.intel.rag.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文本处理工具类
 */
public class TextUtils {

    private static final Logger logger = LoggerFactory.getLogger(TextUtils.class);

    /**
     * 检查文本是否为空
     */
    public static boolean isEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * 清理文本（去除多余空白字符）
     */
    public static String cleanText(String text) {
        if (isEmpty(text)) {
            return "";
        }


        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 截断文本到指定长度
     */
    public static String truncate(String text, int maxLength) {
        if (isEmpty(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /**
     * 统计字符数（中文算1个字符）
     */
    public static int countChars(String text) {
        if (isEmpty(text)) {
            return 0;
        }
        return text.length();
    }

    /**
     * 检查是否包含中文字符
     */
    public static boolean containsChinese(String text) {
        if (isEmpty(text)) {
            return false;
        }
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }

    /**
     * 判断文本是否为短文本
     */
    public static boolean isShortText(String text, int threshold) {
        return countChars(text) <= threshold;
    }
}
