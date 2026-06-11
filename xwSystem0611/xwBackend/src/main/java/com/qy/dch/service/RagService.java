package com.qy.dch.service;

import com.qy.dch.common.ResultVO;

public interface RagService {

    ResultVO getIndexStatus();

    ResultVO triggerIndexing(String startDate, String endDate);

    ResultVO search(String query, Integer topK, Boolean hybrid);

    ResultVO getIndexLog(Integer pageNum, Integer pageSize);

    void scheduledIndexing();
}
