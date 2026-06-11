package com.qy.dch.service.impl;

// JSON处理库 - 阿里巴巴Fastjson
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
// 通用返回结果封装类
import com.qy.dch.common.ResultVO;
// 数据传输对象
import com.qy.dch.dto.ExtractionResultDTO;
import com.qy.dch.dto.OriginTextDTO;
// MyBatis Mapper接口
import com.qy.dch.mapper.ExtractionMapper;
import com.qy.dch.mapper.UygurMapper;
// 服务接口
import com.qy.dch.service.ExtractionService;
// Jakarta注解 - 资源注入
import javax.annotation.Resource;
// Lombok注解 - 日志支持
import lombok.extern.slf4j.Slf4j;
// Spring注解 - 服务组件
import org.springframework.stereotype.Service;
// Spring注解 - 配置值注入
import org.springframework.beans.factory.annotation.Value;

// Java IO类 - 用于HTTP请求
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
// Java网络类 - HTTP连接
import java.net.HttpURLConnection;
import java.net.URL;
// 字符编码
import java.nio.charset.StandardCharsets;
// 并发工具
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * 属性抽取服务实现类
 * <p>
 * 核心功能：
 * 1. 调用算法服务的HTTP接口进行事件抽取
 * 2. 解析抽取结果并存储到数据库
 * 3. 管理抽取状态（已抽取/未抽取）
 * 4. 批量抽取任务管理
 * </p>
 *
 * <p>架构说明：
 * 后端不直接调用LLM，而是通过HTTP请求调用Python算法服务。
 * 这样设计的好处：
 * - 前后端解耦，算法可以独立升级
 * - Python更适合AI/ML任务
 * - 便于多语言协作开发
 * </p>
 *
 * @author system
 */
@Slf4j  // Lombok注解，自动生成日志对象log
@Service  // Spring注解，标记为服务组件
public class ExtractionServiceImpl implements ExtractionService {

    /**
     * 抽取结果Mapper - 用于数据库操作
     * 操作extraction_result表
     */
    @Resource
    private ExtractionMapper extractionMapper;

    /**
     * 原始文本Mapper - 用于查询原始文本
     * 操作origin_text表
     */
    @Resource
    private UygurMapper uygurMapper;

    /**
     * 算法服务地址
     * Python Flask服务运行在5001端口
     * 支持通过环境变量 ALGORITHM_SERVICE_URL 配置
     */
    @Value("${algorithm.service.url:http://localhost:5001}")
    private String algorithmServiceUrl;

    /**
     * 批量抽取任务状态存储
     */
    private static final ConcurrentHashMap<String, BatchTaskState> BATCH_TASKS = new ConcurrentHashMap<>();

    /**
     * 批量抽取线程池
     */
    private static final ExecutorService BATCH_EXECUTOR = Executors.newFixedThreadPool(2);

    /**
     * 批量抽取任务状态类
     */
    private static class BatchTaskState {
        String taskId;
        int total;
        AtomicInteger done = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicBoolean running = new AtomicBoolean(true);
        Integer currentSid;
        List<String> logs = Collections.synchronizedList(new ArrayList<>());
        long startTime = System.currentTimeMillis();

        BatchTaskState(String taskId, int total) {
            this.taskId = taskId;
            this.total = total;
        }

        void addLog(String log) {
            logs.add(log);
            if (logs.size() > 100) {
                logs.remove(0);
            }
        }
    }


    /**
     * 执行事件抽取
     *
     * <p>处理流程：
     * 1. 检查是否已抽取过（除非强制重新抽取）
     * 2. 获取原始文本内容
     * 3. 调用算法服务进行抽取
     * 4. 解析并保存结果
     * 5. 更新抽取状态
     * </p>
     *
     * @param originTextId 原始文本ID
     * @param force 是否强制重新抽取
     * @return 抽取结果
     */
    @Override
    public ResultVO extract(Integer originTextId, Boolean force) {
        // 记录开始日志
        log.info("========== [属性抽取] 开始 ==========");
        log.info("[输入] 文本ID: {}, 强制重新抽取: {}", originTextId, force);

        // 1. 检查是否已抽取（依赖is_extracted字段）
        Integer isExtracted = uygurMapper.selectIsExtracted(originTextId.longValue());
        // 如果已抽取且不是强制模式，返回提示
        if (isExtracted != null && isExtracted == 1 && !force) {
            log.info("文本已抽取（is_extracted=1），返回提示信息");
            // 返回提示信息，前端会弹出确认框
            return ResultVO.error("该报文已完成数据抽取，是否重新抽取该报文数据？");
        }

        // 2. 获取原始文本
        OriginTextDTO text = uygurMapper.selectById(originTextId.longValue());
        // 检查文本是否存在
        if (text == null) {
            log.error("文本不存在，ID: {}", originTextId);
            return ResultVO.error("文本不存在");
        }

        // 3. 调用算法服务进行抽取
        JSONObject extractionResult;
        try {
            // 调用HTTP接口
            extractionResult = callAlgorithmService(text.getContent(), originTextId);
            // 检查返回结果
            if (extractionResult == null) {
                return ResultVO.error("抽取失败：算法服务返回空结果");
            }
        } catch (Exception e) {
            // 记录异常日志
            log.error("调用算法服务失败: {}", e.getMessage(), e);
            return ResultVO.error("抽取失败：" + e.getMessage());
        }

        // 4. 解析并保存结果
        ExtractionResultDTO resultDTO = parseAndSave(originTextId, extractionResult);

        // 5. 更新origin_text表的抽取状态
        try {
            // extracted字段：0=未抽取，1=已抽取
            uygurMapper.updateExtractedStatus(originTextId, 1);
            log.info("更新抽取状态成功，文本ID: {}", originTextId);
        } catch (Exception e) {
            // 状态更新失败不影响主流程，仅记录警告
            log.warn("更新抽取状态失败: {}", e.getMessage());
        }

        // 记录输出日志
        log.info("[输出] 抽取成功，事件数: {}, 结果ID: {}", resultDTO.getTotalEvents(), resultDTO.getId());
        log.info("========== [属性抽取] 完成 ==========");

        // 返回成功结果
        return ResultVO.success(resultDTO);
    }

    /**
     * 获取抽取结果
     *
     * <p>从数据库查询已保存的抽取结果</p>
     *
     * @param originTextId 原始文本ID
     * @return 抽取结果（包含事件列表）
     */
    @Override
    public ResultVO getResult(Integer originTextId) {
        // 从数据库查询结果
        ExtractionResultDTO result = extractionMapper.selectByOriginTextId(originTextId);

        // 检查结果是否存在且包含事件数据
        if (result != null && result.getEventsJson() != null) {
            try {
                // 构建返回的JSON对象
                JSONObject response = new JSONObject();
                // 设置基本信息
                response.put("id", result.getId());
                response.put("originTextId", result.getOriginTextId());
                response.put("extractionTime", result.getExtractionTime());
                response.put("totalEvents", result.getTotalEvents());
                response.put("status", result.getStatus());

                // 解析events JSON字符串为数组
                JSONArray events = parseEventsJson(result.getEventsJson());
                response.put("events", events);

                // 解析labels JSON字符串为数组
                JSONArray labels = result.getLabels();
                response.put("labels", labels);

                // 解析entities JSON字符串为对象
                JSONObject entities = result.getEntities();
                response.put("entities", entities);

                return ResultVO.success(response);
            } catch (Exception e) {
                // 解析失败时返回原始结果
                log.error("解析抽取结果失败: {}", e.getMessage());
                return ResultVO.success(result);
            }
        }
        // 无结果时返回空
        return ResultVO.success(null);
    }

    /**
     * 调用算法服务HTTP接口
     *
     * <p>使用HttpURLConnection发送POST请求到Python算法服务</p>
     *
     * <p>超时设置说明：
     * - 连接超时10秒：建立TCP连接的最大等待时间
     * - 读取超时5分钟：等待响应的最大时间（LLM处理较慢）
     * </p>
     *
     * @param content 要抽取的文本内容
     * @param originTextId 文本ID（用于日志）
     * @return 算法服务返回的JSON结果
     * @throws Exception 网络请求异常
     */
    private JSONObject callAlgorithmService(String content, Integer originTextId) throws Exception {
        // 构建完整URL
        String url = algorithmServiceUrl + "/extract";

        // 构建请求体JSON
        JSONObject requestBody = new JSONObject();
        requestBody.put("text", content);  // 文本内容
        requestBody.put("origin_text_id", originTextId);  // 文本ID

        // 记录请求日志
        log.info("调用算法服务: {}, 文本长度: {}", url, content.length());

        // 声明连接对象
        HttpURLConnection conn = null;
        try {
            // 创建URL对象
            URL urlObj = new URL(url);
            // 打开HTTP连接
            conn = (HttpURLConnection) urlObj.openConnection();

            // 设置请求方法为POST
            conn.setRequestMethod("POST");
            // 设置Content-Type头
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            // 允许输出请求体
            conn.setDoOutput(true);
            // 连接超时：10秒
            conn.setConnectTimeout(10000);
            // 读取超时：20分钟（1200000毫秒）
            // LLM处理需要较长时间，设置长超时
            conn.setReadTimeout(1200000);

            // 发送请求体
            try (OutputStream os = conn.getOutputStream()) {
                // 将JSON字符串转换为字节数组
                byte[] input = requestBody.toJSONString().getBytes(StandardCharsets.UTF_8);
                // 写入输出流
                os.write(input, 0, input.length);
            }

            // 获取HTTP响应码
            int responseCode = conn.getResponseCode();
            log.info("算法服务响应码: {}", responseCode);

            // 检查响应是否成功
            if (responseCode != 200) {
                // 读取错误信息
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder error = new StringBuilder();
                    String line;
                    // 逐行读取错误响应
                    while ((line = reader.readLine()) != null) {
                        error.append(line);
                    }
                    // 记录错误日志
                    log.error("算法服务返回错误: {}", error);
                    // 抛出运行时异常
                    throw new RuntimeException("算法服务返回错误: " + error);
                }
            }

            // 读取成功响应
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                // 逐行读取响应内容
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                // 获取响应字符串
                String responseStr = response.toString();
                log.info("算法服务响应长度: {}", responseStr.length());
                log.info("算法服务响应内容: {}", responseStr);

                // 解析JSON并返回
                return JSON.parseObject(responseStr);
            }

        } finally {
            // 确保关闭连接（释放资源）
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 解析抽取结果并保存到数据库
     *
     * <p>处理算法服务返回的JSON，提取关键信息并存入数据库</p>
     *
     * @param originTextId 原始文本ID
     * @param extractionResult 算法服务返回的完整结果
     * @return 保存后的DTO对象（包含数据库ID）
     */
    private ExtractionResultDTO parseAndSave(Integer originTextId, JSONObject extractionResult) {
        // 创建DTO对象
        ExtractionResultDTO dto = new ExtractionResultDTO();
        // 设置关联的文本ID
        dto.setOriginTextId(originTextId);
        // 设置使用的模型名称
        dto.setModel("GLM-5-hierarchical");
        // 设置状态为完成
        dto.setStatus("completed");

        // 从响应中获取data对象
        JSONObject data = extractionResult.getJSONObject("data");
        if (data != null) {
            // 提取事件数组
            JSONArray events = data.getJSONArray("events");
            if (events != null && !events.isEmpty()) {
                // 构建events JSON对象
                JSONObject eventsWrapper = new JSONObject();
                eventsWrapper.put("events", events);
                // 存储为JSON字符串
                dto.setEventsJson(eventsWrapper.toJSONString());
                // 设置事件总数
                dto.setTotalEvents(events.size());
            } else {
                // 无事件时设置空数组
                dto.setEventsJson("{\"events\":[]}");
                dto.setTotalEvents(0);
            }

            // 提取标签数组
            JSONArray labels = data.getJSONArray("labels");
            if (labels != null && !labels.isEmpty()) {
                dto.setLabelsJson(labels.toJSONString());
            } else {
                dto.setLabelsJson("[]");
            }

            // 提取实体对象
            JSONObject entities = data.getJSONObject("entities");
            if (entities != null && !entities.isEmpty()) {
                dto.setEntitiesJson(entities.toJSONString());
            } else {
                dto.setEntitiesJson("{}");
            }

            // 记录统计信息日志
            log.info("抽取统计 - 段落数: {}, LLM调用: {}, 跳过: {}, 事件数: {}, 标签: {}, 实体: {}",
                    data.getInteger("paragraph_count"),  // 段落总数
                    data.getInteger("llm_calls"),  // LLM调用次数
                    data.getInteger("llm_calls_saved"),  // 跳过的段落数
                    dto.getTotalEvents(),  // 抽取的事件数
                    dto.getLabelsJson(),  // 标签
                    dto.getEntitiesJson());  // 实体
        } else {
            // data为空时的默认处理
            dto.setEventsJson("{\"events\":[]}");
            dto.setTotalEvents(0);
            dto.setLabelsJson("[]");
            dto.setEntitiesJson("{}");
        }

        // 保存到数据库（插入或更新）
        extractionMapper.insertOrUpdate(dto);
        log.info("抽取结果已保存，ID: {}, 事件数: {}", dto.getId(), dto.getTotalEvents());

        return dto;
    }

    /**
     * 解析events JSON数组
     *
     * <p>处理数据库中存储的JSON字符串，转换为JSONArray</p>
     *
     * <p>支持两种格式：
     * 1. 包含events键的对象：{"events": [...]}
     * 2. 直接的数组：[...]
     * </p>
     *
     * @param jsonContent JSON字符串
     * @return 事件数组
     */
    private JSONArray parseEventsJson(String jsonContent) {
        // 检查输入是否为空
        if (jsonContent == null || jsonContent.isEmpty()) {
            return new JSONArray();
        }

        try {
            // 先尝试解析为对象
            JSONObject root = JSON.parseObject(jsonContent);
            // 检查是否包含events键
            if (root.containsKey("events")) {
                return root.getJSONArray("events");
            }
            // 如果不包含events键，尝试直接解析为数组
            return JSON.parseArray(jsonContent);
        } catch (Exception e) {
            // 解析失败时记录警告并返回空数组
            log.warn("解析events JSON失败: {}", e.getMessage());
            return new JSONArray();
        }
    }

    /**
     * 启动批量抽取任务
     */
    @Override
    public Map<String, Object> startBatchExtraction(String startDate, String endDate, String scope) {
        log.info("startBatchExtraction: startDate={}, endDate={}, scope={}", startDate, endDate, scope);

        // 查询符合条件的报文ID列表
        Integer isExtracted = "unextracted".equals(scope) ? 0 : null;
        List<Integer> ids = uygurMapper.selectIdsByTimeRange(startDate, endDate, isExtracted);

        if (ids.isEmpty()) {
            log.warn("未找到符合条件的报文");
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", null);
            result.put("totalCount", 0);
            return result;
        }

        // 生成任务ID
        String taskId = "batch_" + System.currentTimeMillis();
        BatchTaskState state = new BatchTaskState(taskId, ids.size());
        BATCH_TASKS.put(taskId, state);

        log.info("批量抽取任务启动: taskId={}, 总数={}", taskId, ids.size());
        state.addLog("任务启动，共 " + ids.size() + " 篇报文待抽取");

        // 异步执行批量抽取
        BATCH_EXECUTOR.submit(() -> {
            for (Integer id : ids) {
                if (!state.running.get()) {
                    state.addLog("任务已停止");
                    break;
                }

                state.currentSid = id;
                state.addLog("开始抽取报文 " + id);

                try {
                    ResultVO result = extract(id, false);
                    if (result.getCode() == 1) {
                        state.done.incrementAndGet();
                        state.addLog("报文 " + id + " 抽取成功");
                    } else {
                        state.failed.incrementAndGet();
                        state.addLog("报文 " + id + " 抽取失败: " + result.getMsg());
                    }
                } catch (Exception e) {
                    state.failed.incrementAndGet();
                    state.addLog("报文 " + id + " 抽取异常: " + e.getMessage());
                    log.error("批量抽取异常: id={}", id, e);
                }
            }

            state.running.set(false);
            state.currentSid = null;
            state.addLog("任务完成，成功 " + state.done.get() + " 篇，失败 " + state.failed.get() + " 篇");
            log.info("批量抽取任务完成: taskId={}, 成功={}, 失败={}", taskId, state.done.get(), state.failed.get());
        });

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("totalCount", ids.size());
        return result;
    }

    /**
     * 查询批量抽取任务进度
     */
    @Override
    public Map<String, Object> getBatchProgress(String taskId) {
        BatchTaskState state = BATCH_TASKS.get(taskId);
        if (state == null) {
            return null;
        }

        Map<String, Object> progress = new HashMap<>();
        progress.put("taskId", taskId);
        progress.put("total", state.total);
        progress.put("done", state.done.get());
        progress.put("failed", state.failed.get());
        progress.put("running", state.running.get());
        progress.put("currentSid", state.currentSid);
        progress.put("logs", new ArrayList<>(state.logs));

        // 计算预计剩余时间
        if (state.done.get() > 0 && state.running.get()) {
            long elapsed = System.currentTimeMillis() - state.startTime;
            long avgTime = elapsed / state.done.get();
            int remaining = state.total - state.done.get() - state.failed.get();
            long eta = avgTime * remaining / 1000; // 秒
            progress.put("eta", eta);
        } else {
            progress.put("eta", 0);
        }

        return progress;
    }

    /**
     * 停止批量抽取任务
     */
    @Override
    public boolean stopBatchTask(String taskId) {
        log.info("stopBatchTask: taskId={}", taskId);
        BatchTaskState state = BATCH_TASKS.get(taskId);
        if (state != null) {
            state.running.set(false);
            state.addLog("收到停止指令");
            return true;
        }
        return false;
    }
}