package com.intel.rag.service;

import com.intel.rag.entity.DataRecord;
import com.intel.rag.repository.DataRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据导入服务测试
 * 使用@DataJpaTest避免加载完整Spring上下文
 */
@DataJpaTest
@TestPropertySource(locations = "classpath:application-test.properties")
class DataImportServiceTest {

    @Autowired
    private DataRecordRepository dataRecordRepository;

    @Test
    void testDataRecordRepositoryExists() {
        assertNotNull(dataRecordRepository);
    }

    @Test
    void testSaveAndFindDataRecord() {
        // 清空测试数据
        dataRecordRepository.deleteAll();

        // 插入测试数据
        DataRecord record = new DataRecord();
        record.setTitle("测试标题");
        record.setContent("测试内容");
        record.setType(1);
        record.setModalType("文字报");
        record.setIsExtracted(false);

        DataRecord saved = dataRecordRepository.save(record);

        assertNotNull(saved.getSid());
        assertEquals("测试标题", saved.getTitle());
        assertEquals("测试内容", saved.getContent());
    }

    @Test
    void testCountAll() {
        dataRecordRepository.deleteAll();

        DataRecord record1 = new DataRecord();
        record1.setTitle("标题1");
        record1.setContent("内容1");
        record1.setType(1);
        record1.setModalType("文字报");
        dataRecordRepository.save(record1);

        DataRecord record2 = new DataRecord();
        record2.setTitle("标题2");
        record2.setContent("内容2");
        record2.setType(2);
        record2.setModalType("图片报");
        dataRecordRepository.save(record2);

        long count = dataRecordRepository.countAll();
        assertEquals(2, count);
    }

    @Test
    void testFindByType() {
        dataRecordRepository.deleteAll();

        DataRecord record1 = new DataRecord();
        record1.setTitle("类型1记录");
        record1.setContent("内容");
        record1.setType(1);
        record1.setModalType("文字报");
        dataRecordRepository.save(record1);

        DataRecord record2 = new DataRecord();
        record2.setTitle("类型2记录");
        record2.setContent("内容");
        record2.setType(2);
        record2.setModalType("图片报");
        dataRecordRepository.save(record2);

        var type1Records = dataRecordRepository.findByType(1);
        assertEquals(1, type1Records.size());
        assertEquals("类型1记录", type1Records.get(0).getTitle());
    }
}
