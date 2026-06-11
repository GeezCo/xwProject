package com.intel.rag.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文文本分析器
 * 使用正则表达式进行简单的中文分词（替代方案：可集成IKAnalyzer）
 */
@Slf4j
@Component
public class ChineseTextAnalyzer {

    // 中文句子分隔符
    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
        "[。！？；\\n]+|(?<=[。！？；])(?=[^\\s])"
    );

    // 中文字符模式（包含常用标点）
    private static final Pattern CHINESE_PATTERN = Pattern.compile(
        "[\\u4e00-\\u9fa5]+"
    );

    // 英文单词模式
    private static final Pattern ENGLISH_PATTERN = Pattern.compile(
        "[a-zA-Z]+"
    );

    // 数字模式
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
        "\\d+(\\.\\d+)?"
    );

    /**
     * 分词（简单实现：基于二元切分）
     * 生产环境建议集成IKAnalyzer或其他专业分词器
     */
    public List<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> tokens = new ArrayList<>();

        // 提取中文词汇（二元切分）
        Matcher chineseMatcher = CHINESE_PATTERN.matcher(text);
        while (chineseMatcher.find()) {
            String chinese = chineseMatcher.group();
            // 二元切分
            for (int i = 0; i < chinese.length() - 1; i++) {
                tokens.add(chinese.substring(i, i + 2));
            }
            // 保留完整词
            if (chinese.length() <= 4) {
                tokens.add(chinese);
            }
        }

        // 提取英文单词
        Matcher englishMatcher = ENGLISH_PATTERN.matcher(text);
        while (englishMatcher.find()) {
            tokens.add(englishMatcher.group().toLowerCase());
        }

        // 提取数字
        Matcher numberMatcher = NUMBER_PATTERN.matcher(text);
        while (numberMatcher.find()) {
            tokens.add(numberMatcher.group());
        }

        return tokens;
    }

    /**
     * 按句子切分文本
     */
    public List<String> splitBySentence(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> sentences = new ArrayList<>();
        String[] parts = SENTENCE_PATTERN.split(text);

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }

        return sentences;
    }

    /**
     * 生成trigram用于PostgreSQL全文检索
     */
    public String generateTrigram(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        StringBuilder trigrams = new StringBuilder();
        String cleanText = text.replaceAll("\\s+", " ").trim();

        for (int i = 0; i < cleanText.length() - 2; i++) {
            if (trigrams.length() > 0) {
                trigrams.append(" ");
            }
            trigrams.append(cleanText.substring(i, i + 3));
        }

        return trigrams.toString();
    }

    /**
     * 简单的句子边界检测
     */
    public boolean isSentenceBoundary(int position, String text) {
        if (position <= 0 || position >= text.length()) {
            return true;
        }

        char prev = text.charAt(position - 1);
        char curr = text.charAt(position);

        // 句子结束符
        if (prev == '。' || prev == '！' || prev == '？' || prev == '；' || prev == '\n') {
            return true;
        }

        // 英文句子结束符（后跟空格和大写字母）
        if ((prev == '.' || prev == '!' || prev == '?') && curr == ' ') {
            return true;
        }

        return false;
    }
}
