package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.DocumentParserProperties;
import com.qy.dch.rag.config.OcrProperties;
import com.qy.dch.rag.model.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for DocxMixedParserService
 */
class DocxMixedParserServiceTest {

    @Mock
    private OcrService ocrService;

    @Mock
    private OcrProperties ocrProps;

    @Mock
    private DocumentParserProperties docProps;

    @Mock
    private OcrProperties.ImageProperties imageOcrProps;

    private DocxMixedParserService parserService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(ocrProps.getImage()).thenReturn(imageOcrProps);
        parserService = new DocxMixedParserService(ocrService, ocrProps, docProps);
    }

    @Test
    void testParseDocxNoImages_OcrDisabled() throws IOException {
        // Given: OCR is disabled
        when(imageOcrProps.isEnableOcr()).thenReturn(false);

        // Create a simple text-only docx for testing
        Path testFile = createSimpleDocx();

        // When
        ParsedDocument result = parserService.parse(testFile);

        // Then
        assertNotNull(result);
        assertNotNull(result.getContent());
        assertFalse(result.getContent().isEmpty(), "Should extract text from docx");
        assertEquals("docx", result.getSource());
        assertEquals("mixed", result.getType());

        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertEquals(testFile.toString(), metadata.get("filePath"));

        // Verify OCR was not called since no images present
        verify(ocrService, never()).recognizeText(any());
    }

    @Test
    void testParseDocxWithImages_OcrEnabled() throws IOException {
        // Given: OCR is enabled
        when(imageOcrProps.isEnableOcr()).thenReturn(true);
        when(imageOcrProps.getMaxSizeMb()).thenReturn(10);
        when(ocrService.recognizeText(any())).thenReturn("OCR extracted text from image");

        // Create docx with embedded image (mock scenario)
        Path testFile = createDocxWithImage();

        // When
        ParsedDocument result = parserService.parse(testFile);

        // Then
        assertNotNull(result);
        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("OCR extracted text from image"),
                   "Should include OCR text from embedded images");
    }

    @Test
    void testParseDocxWithImages_OcrDisabled() throws IOException {
        // Given: OCR is disabled
        when(imageOcrProps.isEnableOcr()).thenReturn(false);

        Path testFile = createDocxWithImage();

        // When
        ParsedDocument result = parserService.parse(testFile);

        // Then
        assertNotNull(result);
        assertNotNull(result.getContent());
        // Should still extract text paragraphs, just skip images
        verify(ocrService, never()).recognizeText(any());
    }

    @Test
    void testParseDocxWithImages_OcrFails() throws IOException {
        // Given: OCR is enabled but throws exception
        when(imageOcrProps.isEnableOcr()).thenReturn(true);
        when(imageOcrProps.getMaxSizeMb()).thenReturn(10);
        when(ocrService.recognizeText(any())).thenThrow(new RuntimeException("OCR failure"));

        Path testFile = createDocxWithImage();

        // When
        ParsedDocument result = parserService.parse(testFile);

        // Then: Should gracefully degrade, continue parsing text
        assertNotNull(result);
        assertNotNull(result.getContent());
        // Should not propagate exception, just log and continue
    }

    @Test
    void testSupportsDocxMimeType() {
        assertTrue(parserService.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void testDoesNotSupportOtherMimeTypes() {
        assertFalse(parserService.supports("application/pdf"));
        assertFalse(parserService.supports("text/plain"));
        assertFalse(parserService.supports("application/msword")); // old .doc format
    }

    // Helper methods to create test files

    /**
     * Create a minimal valid DOCX file with text only
     */
    private Path createSimpleDocx() throws IOException {
        // Copy test resource or create minimal docx
        Path docxPath = tempDir.resolve("test_simple.docx");

        // For now, copy from test resources if available
        // Otherwise, create a minimal valid docx structure
        try (InputStream is = getClass().getResourceAsStream("/test_documents/simple.docx")) {
            if (is != null) {
                Files.copy(is, docxPath);
            } else {
                // Create minimal docx programmatically using POI
                createMinimalDocxWithText(docxPath, "This is a test document with plain text.");
            }
        }

        return docxPath;
    }

    /**
     * Create a DOCX file with embedded images
     */
    private Path createDocxWithImage() throws IOException {
        Path docxPath = tempDir.resolve("test_with_image.docx");

        try (InputStream is = getClass().getResourceAsStream("/test_documents/with_image.docx")) {
            if (is != null) {
                Files.copy(is, docxPath);
            } else {
                // Create minimal docx with image programmatically
                createMinimalDocxWithImage(docxPath);
            }
        }

        return docxPath;
    }

    private void createMinimalDocxWithText(Path path, String text) throws IOException {
        // Use POI to create a minimal valid DOCX
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
        run.setText(text);

        try (java.io.FileOutputStream out = new java.io.FileOutputStream(path.toFile())) {
            doc.write(out);
        }
        doc.close();
    }

    private void createMinimalDocxWithImage(Path path) throws IOException {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();

        // Add text paragraph
        org.apache.poi.xwpf.usermodel.XWPFParagraph para1 = doc.createParagraph();
        para1.createRun().setText("Text before image");

        // Add image (1x1 red pixel PNG)
        byte[] imageBytes = createTestImageBytes();
        org.apache.poi.xwpf.usermodel.XWPFParagraph para2 = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun run = para2.createRun();
        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(imageBytes)) {
            run.addPicture(bis, org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, "test.png",
                          org.apache.poi.util.Units.toEMU(100), org.apache.poi.util.Units.toEMU(100));
        }

        // Add text after image
        org.apache.poi.xwpf.usermodel.XWPFParagraph para3 = doc.createParagraph();
        para3.createRun().setText("Text after image");

        try (java.io.FileOutputStream out = new java.io.FileOutputStream(path.toFile())) {
            doc.write(out);
        }
        doc.close();
    }

    /**
     * Create a minimal 1x1 red pixel PNG
     */
    private byte[] createTestImageBytes() {
        // Minimal PNG: 1x1 red pixel
        return new byte[] {
            (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,  // PNG signature
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,        // IHDR chunk
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,        // 1x1 dimensions
            0x08, 0x02, 0x00, 0x00, 0x00, (byte)0x90, 0x77, 0x53, (byte)0xDE,
            0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,        // IDAT chunk
            0x08, (byte)0xD7, 0x63, (byte)0xF8, (byte)0xCF, (byte)0xC0, 0x00, 0x00,
            0x03, 0x01, 0x01, 0x00, 0x18, (byte)0xDD, (byte)0x8D, (byte)0xB4,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,        // IEND chunk
            (byte)0xAE, 0x42, 0x60, (byte)0x82
        };
    }
}
