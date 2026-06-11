package com.intel.rag.repository;

import com.intel.rag.entity.DataRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 数据记录仓储接口
 */
@Repository
public interface DataRecordRepository extends JpaRepository<DataRecord, Integer> {

    /**
     * 根据类型查询
     */
    List<DataRecord> findByType(Integer type);

    /**
     * 根据类型分页查询
     */
    Page<DataRecord> findByType(Integer type, Pageable pageable);

    /**
     * 根据模态类型查询
     */
    List<DataRecord> findByModalType(String modalType);

    /**
     * 根据是否提取查询
     */
    List<DataRecord> findByIsExtracted(Boolean isExtracted);

    /**
     * 统计总记录数
     */
    @Query("SELECT COUNT(d) FROM DataRecord d")
    long countAll();

    /**
     * 按ID批量查询
     */
    @Query("SELECT d FROM DataRecord d WHERE d.sid IN :ids")
    List<DataRecord> findBySidIn(List<Integer> ids);
}
