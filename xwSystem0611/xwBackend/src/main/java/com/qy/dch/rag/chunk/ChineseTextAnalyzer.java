package com.qy.dch.rag.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ChineseTextAnalyzer {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
        "[。！？；\\n]+|(?<=[。！？；])(?=[^\\s])"
    );

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
}
