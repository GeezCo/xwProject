package com.intel.rag.service;

import com.intel.rag.config.DocumentParserProperties;
import com.intel.rag.model.DocumentChunk;
import com.intel.rag.util.ChineseTextAnalyzer;
import com.intel.rag.util.IdGenerator;
import com.intel.rag.util.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档切片服务
 * 实现混合切片策略，支持语义完整性
 */
@Slf4j
@Service
public class DocumentChunkService {

    private final DocumentParserProperties parserProperties;
    private final ChineseTextAnalyzer chineseTextAnalyzer;

    public DocumentChunkService(DocumentParserProperties parserProperties,
                                ChineseTextAnalyzer chineseTextAnalyzer) {
        this.parserProperties = parserProperties;
        this.chineseTextAnalyzer = chineseTextAnalyzer;
    }

    /**
     * 对文档内容进行切片（支持语义完整性）
     *
     * @param documentId 文档ID
     * @param content    文档内容
     * @param metadata   文档元数据
     * @return 文档切片列表
     */
    public List<DocumentChunk> chunkDocument(String documentId, String content, Map<String, Object> metadata) {
        log.debug("开始对文档进行切片: documentId={}, contentLength={}", documentId, content.length());

        List<DocumentChunk> chunks = new ArrayList<>();
        int contentLength = content.length();

        // 短文本：不切片
        if (contentLength <= parserProperties.getChunkStrategy().getShortTextMaxLength()) {
            chunks.add(createChunk(documentId, content, 0, "short", metadata));
            log.debug("短文本，不切片: length={}", contentLength);
            return chunks;
        }

        // 中等文本：按句子边界切片
        if (contentLength <= parserProperties.getChunkStrategy().getMediumChunkMaxLength()) {
            chunks.addAll(chunkBySentence(documentId, content, "medium", metadata));
            log.debug("中等文本，按句子切片: chunksCount={}", chunks.size());
            return chunks;
        }

        // 长文本：按句子边界滑动窗口切片
        chunks.addAll(chunkBySentenceWithOverlap(documentId, content, "long", metadata));
        log.debug("长文本，按句子滑动窗口切片: chunksCount={}", chunks.size());

        return chunks;
    }

    /**
     * 按句子边界切片（中等文本）
     */
    private List<DocumentChunk> chunkBySentence(String documentId, String content,
                                                String chunkType, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // 按句子切分
        List<String> sentences = chineseTextAnalyzer.splitBySentence(content);

        // 按目标长度合并句子
        int targetLength = parserProperties.getChunkStrategy().getMediumChunkLength();
        int currentIndex = 0;
        StringBuilder chunkBuilder = new StringBuilder();

        for (String sentence : sentences) {
            if (chunkBuilder.length() + sentence.length() <= targetLength) {
                chunkBuilder.append(sentence).append("\n");
            } else {
                // 达到目标长度，创建切片
                if (chunkBuilder.length() > 0) {
                    chunks.add(createChunk(documentId, chunkBuilder.toString().trim(),
                        currentIndex++, chunkType, metadata));
                    chunkBuilder = new StringBuilder();
                }

                // 单个句子过长，需要进一步切分
                if (sentence.length() > targetLength) {
                    chunks.addAll(chunkByFixedSize(documentId, sentence, targetLength,
                        currentIndex, chunkType, metadata));
                    currentIndex += chunks.size() - currentIndex;
                } else {
                    chunkBuilder.append(sentence).append("\n");
                }
            }
        }

        // 处理剩余内容
        if (chunkBuilder.length() > 0) {
            chunks.add(createChunk(documentId, chunkBuilder.toString().trim(),
                currentIndex, chunkType, metadata));
        }

        return chunks;
    }

    /**
     * 按句子边界滑动窗口切片（长文本）
     */
    private List<DocumentChunk> chunkBySentenceWithOverlap(String documentId, String content,
                                                          String chunkType, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // 按句子切分
        List<String> sentences = chineseTextAnalyzer.splitBySentence(content);

        int targetLength = parserProperties.getChunkStrategy().getLongChunkLength();
        int overlapLength = parserProperties.getChunkStrategy().getLongChunkOverlap();
        int currentIndex = 0;

        List<String> currentChunkSentences = new ArrayList<>();
        int currentChunkLength = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int sentenceLength = sentence.length();

            // 如果当前切片长度 + 新句子 <= 目标长度，添加句子
            if (currentChunkLength + sentenceLength <= targetLength) {
                currentChunkSentences.add(sentence);
                currentChunkLength += sentenceLength;
            } else {
                // 创建当前切片
                if (!currentChunkSentences.isEmpty()) {
                    String chunkContent = String.join("\n", currentChunkSentences);
                    chunks.add(createChunk(documentId, chunkContent,
                        currentIndex++, chunkType, metadata));
                }

                // 计算重叠：保留部分句子作为重叠
                currentChunkSentences = new ArrayList<>();
                currentChunkLength = 0;

                // 回退overlapLength字符对应的句子数
                if (overlapLength > 0 && i > 0) {
                    int overlapStart = findOverlapStart(sentences, i, overlapLength);
                    for (int j = overlapStart; j < i; j++) {
                        currentChunkSentences.add(sentences.get(j));
                        currentChunkLength += sentences.get(j).length();
                    }
                }

                // 添加当前句子
                currentChunkSentences.add(sentence);
                currentChunkLength += sentenceLength;
            }
        }

        // 处理剩余句子
        if (!currentChunkSentences.isEmpty()) {
            String chunkContent = String.join("\n", currentChunkSentences);
            chunks.add(createChunk(documentId, chunkContent, currentIndex, chunkType, metadata));
        }

        return chunks;
    }

    /**
     * 找到重叠区域的起始句子索引
     */
    private int findOverlapStart(List<String> sentences, int currentIndex, int overlapLength) {
        int accumulatedLength = 0;
        int startIndex = currentIndex;

        // 向前回退，累积长度达到overlapLength
        while (startIndex > 0 && accumulatedLength < overlapLength) {
            startIndex--;
            accumulatedLength += sentences.get(startIndex).length();
        }

        return startIndex;
    }

    /**
     * 固定长度切片（无重叠）
     */
    private List<DocumentChunk> chunkByFixedSize(String documentId, String content, int chunkSize,
                                                  int overlap, String chunkType, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int index = 0;
        int position = 0;

        while (position < content.length()) {
            int end = Math.min(position + chunkSize, content.length());
            String chunkContent = content.substring(position, end);

            chunks.add(createChunk(documentId, chunkContent, index++, chunkType, metadata));
            position += chunkSize;
        }

        return chunks;
    }

    /**
     * 滑动窗口切片（有重叠）
     */
    private List<DocumentChunk> chunkBySlidingWindow(String documentId, String content, int chunkSize,
                                                      int overlap, String chunkType, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int index = 0;
        int position = 0;

        while (position < content.length()) {
            int end = Math.min(position + chunkSize, content.length());
            String chunkContent = content.substring(position, end);

            chunks.add(createChunk(documentId, chunkContent, index++, chunkType, metadata));

            // 滑动窗口：下一个位置 = 当前位置 + (chunkSize - overlap)
            position += (chunkSize - overlap);

            // 如果剩余内容小于overlap，直接跳到最后
            if (position + overlap >= content.length() && end < content.length()) {
                position = content.length();
            }
        }

        return chunks;
    }

    /**
     * 创建单个切片
     */
    private DocumentChunk createChunk(String documentId, String content, int index,
                                      String chunkType, Map<String, Object> metadata) {
        String cleanContent = TextUtils.cleanText(content);

        return DocumentChunk.builder()
                .id(IdGenerator.generateChunkId(documentId, index))
                .documentId(documentId)
                .content(cleanContent)
                .chunkIndex(index)
                .chunkType(chunkType)
                .length(cleanContent.length())
                .metadata(new HashMap<>(metadata))
                .build();
    }

    /**
     * 验证切片配置
     */
    public boolean validateChunkConfig() {
        var strategy = parserProperties.getChunkStrategy();

        if (strategy.getShortTextMaxLength() <= 0) {
            log.error("shortTextMaxLength must be positive");
            return false;
        }

        if (strategy.getMediumChunkLength() <= strategy.getShortTextMaxLength()) {
            log.error("mediumChunkLength must be greater than shortTextMaxLength");
            return false;
        }

        if (strategy.getLongChunkLength() <= strategy.getMediumChunkLength()) {
            log.error("longChunkLength must be greater than mediumChunkLength");
            return false;
        }

        if (strategy.getLongChunkOverlap() >= strategy.getLongChunkLength()) {
            log.error("longChunkOverlap must be less than longChunkLength");
            return false;
        }

        return true;
    }
}
