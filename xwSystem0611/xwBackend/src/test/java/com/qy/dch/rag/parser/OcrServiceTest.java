package com.qy.dch.rag.parser;

import com.qy.dch.rag.config.OcrProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class OcrServiceTest {

    @Test
    void recognizeText_emptyBytes_returnsEmptyString() {
        OcrProperties props = new OcrProperties();
        OcrService service = new OcrService(props, Executors.newSingleThreadExecutor());
        assertEquals("", service.recognizeText(new byte[0]));
        assertEquals("", service.recognizeText((byte[]) null));
    }

    @Test
    void recognizeText_invalidBytes_returnsEmptyString() {
        OcrProperties props = new OcrProperties();
        OcrService service = new OcrService(props, Executors.newSingleThreadExecutor());
        assertEquals("", service.recognizeText(new byte[]{1, 2, 3, 4}));
    }
}
