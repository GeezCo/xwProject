package com.qy.dch.rag.chunk;

import com.qy.dch.rag.config.RagProperties;
import com.qy.dch.rag.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChunkService {

    @Autowired
    private RagProperties ragProperties;

    private final ChineseTextAnalyzer textAnalyzer = new ChineseTextAnalyzer();

    public List<DocumentChunk> chunkDocument(String documentId, String content, Map<String, Object> metadata) {
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        int contentLength = content.length();
        int shortThreshold = ragProperties.getChunk().getShortThreshold();
        int mediumThreshold = ragProperties.getChunk().getMediumThreshold();

        if (contentLength <= shortThreshold) {
            chunks.add(createChunk(documentId, content, 0, "short", metadata));
        } else if (contentLength <= mediumThreshold) {
            chunks.addAll(chunkBySentence(documentId, content, "medium", metadata));
        } else {
            chunks.addAll(chunkBySentenceWithOverlap(documentId, content, "long", metadata));
        }

        log.debug("切片完成: docId={}, chunks={}", documentId, chunks.size());
        return chunks;
    }

    private List<DocumentChunk> chunkBySentence(String documentId, String content,
                                                 String chunkType, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> sentences = textAnalyzer.splitBySentence(content);
        int targetLength = ragProperties.getChunk().getMediumSize();
        int currentIndex = 0;
        StringBuilder chunkBuilder = new StringBuilder();

        for (String sentence : sentences) {
            if (chunkBuilder.length() + sentence.length() <= targetLength) {
                chunkBuilder.append(sentence).append("\n");
            } else {
                if (chunkBuilder.length() > 0) {
                    chunks.add(createChunk(documentId, chunkBuilder.toString().trim(),
                            currentIndex++, chunkType, metadata));
                    chunkBuilder = new StringBuilder();
                }
                if (sentence.length() > targetLength) {
                    chunks.addAll(chunkByFixedSize(documentId, sentence, targetLength,
                            currentIndex, chunkType, metadata));
                    currentIndex = chunks.size();
                } else {
                    chunkBuilder.append(sentence).append("\n");
                }
            }
        }

        if (chunkBuilder.length() > 0) {
            chunks.add(createChunk(documentId, chunkBuilder.toString().trim(),
                    currentIndex, chunkType, metadata));
        }
        return chunks;
    }

    private List<DocumentChunk> chunkBySentenceWithOverlap(String documentId, String content,
                                                            String chunkType, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> sentences = textAnalyzer.splitBySentence(content);
        int targetLength = ragProperties.getChunk().getLongSize();
        int overlapLength = ragProperties.getChunk().getOverlap();
        int currentIndex = 0;

        List<String> currentChunkSentences = new ArrayList<>();
        int currentChunkLength = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int sentenceLength = sentence.length();

            if (currentChunkLength + sentenceLength <= targetLength) {
                currentChunkSentences.add(sentence);
                currentChunkLength += sentenceLength;
            } else {
                if (!currentChunkSentences.isEmpty()) {
                    String chunkContent = String.join("\n", currentChunkSentences);
                    chunks.add(createChunk(documentId, chunkContent, currentIndex++, chunkType, metadata));
                }
                currentChunkSentences = new ArrayList<>();
                currentChunkLength = 0;
                if (overlapLength > 0 && i > 0) {
                    int overlapStart = findOverlapStart(sentences, i, overlapLength);
                    for (int j = overlapStart; j < i; j++) {
                        currentChunkSentences.add(sentences.get(j));
                        currentChunkLength += sentences.get(j).length();
                    }
                }
                currentChunkSentences.add(sentence);
                currentChunkLength += sentenceLength;
            }
        }

        if (!currentChunkSentences.isEmpty()) {
            String chunkContent = String.join("\n", currentChunkSentences);
            chunks.add(createChunk(documentId, chunkContent, currentIndex, chunkType, metadata));
        }
        return chunks;
    }

    private int findOverlapStart(List<String> sentences, int currentIndex, int overlapLength) {
        int accumulatedLength = 0;
        int startIndex = currentIndex;
        while (startIndex > 0 && accumulatedLength < overlapLength) {
            startIndex--;
            accumulatedLength += sentences.get(startIndex).length();
        }
        return startIndex;
    }

    private List<DocumentChunk> chunkByFixedSize(String documentId, String content, int chunkSize,
                                                  int startIndex, String chunkType, Map<String, Object> metadata) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int index = startIndex;
        int position = 0;
        while (position < content.length()) {
            int end = Math.min(position + chunkSize, content.length());
            String chunkContent = content.substring(position, end);
            chunks.add(createChunk(documentId, chunkContent, index++, chunkType, metadata));
            position += chunkSize;
        }
        return chunks;
    }

    private DocumentChunk createChunk(String documentId, String content, int index,
                                       String chunkType, Map<String, Object> metadata) {
        String cleanContent = content.replaceAll("\\s+", " ").trim();
        return DocumentChunk.builder()
                .id(IdGenerator.generateChunkId(documentId, index))
                .documentId(documentId)
                .content(cleanContent)
                .chunkIndex(index)
                .chunkType(chunkType)
                .length(cleanContent.length())
                .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
                .build();
    }
}
