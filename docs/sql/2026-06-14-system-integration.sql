-- ================================================================
-- 20260613 系统对接 - 业务表新增 + 字段扩展
-- 目标库：36.141.21.176:9204 / uygur_project
-- 约定（与 2026-06-13 ID 全局化迁移保持一致）：
--   * 所有业务主键 id 统一 VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
--     - 取消 AUTO_INCREMENT；改由 Java 侧 MyBatis-Plus 雪花算法生成 19 位字符串
--     - ascii_bin 让索引体积仅为 utf8mb4 的 1/4，比较走二进制速度最快
--   * 业务文本字段统一 utf8mb4 / utf8mb4_0900_ai_ci（MySQL 8 默认排序规则）
--   * 创建/更新时间统一 DATETIME DEFAULT CURRENT_TIMESTAMP
-- ================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------
-- 1. origin_text 增加 category 列（一级分类）
--    用于「03-search.html」左侧目录树第一层：开源信息 / HZ报 / JZ报 / 未分类
--    入库时由 CategoryClassifier 计算：
--      send_unit_name 匹配 /HZ\d+报/  → 'HZ报'
--      content 包含 'JZX'            → 'JZ报'
--      content 包含 '入网QB'          → '开源信息'
--      其余                          → '未分类'
-- ---------------------------------------------------------------
ALTER TABLE `origin_text`
  ADD COLUMN `category` VARCHAR(20)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
    NOT NULL DEFAULT '未分类'
    COMMENT '一级分类：开源信息/HZ报/JZ报/未分类（入库时自动计算）'
    AFTER `send_unit_name`,
  ADD INDEX `idx_category` (`category`) COMMENT '目录树一级聚合用',
  ADD INDEX `idx_category_send_unit` (`category`, `send_unit_name`) COMMENT '二级目录聚合用';

-- 回填存量数据（一次性脚本，迁移后执行）
UPDATE `origin_text` SET `category` =
  CASE
    WHEN `send_unit_name` REGEXP '^HZ[0-9]+报' THEN 'HZ报'
    WHEN `content` LIKE '%JZX%'              THEN 'JZ报'
    WHEN `content` LIKE '%入网QB%'            THEN '开源信息'
    ELSE '未分类'
  END
WHERE `category` = '未分类';

-- ---------------------------------------------------------------
-- 2. target_analysis - 目标分析结果表
--    存储算法对单条报文做「目标分析」后抽取出的多个目标条目。
--    每行 = 一个报文中的一个目标。
--    数据来源：POST http://algorithm-service/api/target/analyze 的返回。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `target_analysis` (
  `id`               VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
                     NOT NULL COMMENT '主键ID（雪花算法生成的19位字符串）',
  `origin_text_id`   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
                     NOT NULL COMMENT '关联报文ID → origin_text.id',
  `region_name`      VARCHAR(255) DEFAULT NULL COMMENT '区域名称，例如「哈尔科夫」',
  `target_name`      VARCHAR(255) NOT NULL COMMENT '目标名称（经别名映射后的标准名）',
  `raw_target_name`  VARCHAR(255) DEFAULT NULL COMMENT '算法返回的原始目标名（未别名映射）',
  `target_type`      VARCHAR(100) DEFAULT NULL COMMENT '目标类型，例如「军事设施」',
  `found_time`       VARCHAR(50)  DEFAULT NULL COMMENT '算法识别的发现时间（字符串原样保留）',
  `description`      TEXT         COMMENT '情况描述（针对该目标的那段文字）',
  `attachment_url`   TEXT         COMMENT '对应附件URL（图片/视频，可能为 null）',
  `is_fused`         TINYINT(1)   NOT NULL DEFAULT 0
                     COMMENT '是否已参与融合：0-否 1-是（融合接口完成后置1）',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                     ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_origin_text_id` (`origin_text_id`),
  KEY `idx_target_name`    (`target_name`),
  KEY `idx_region_name`    (`region_name`),
  KEY `idx_is_fused`       (`is_fused`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='目标分析结果（每行=一个报文中的一个目标）';

-- ---------------------------------------------------------------
-- 3. target_alias - 目标别名映射表
--    某些目标有多个名称（如「xxx弹药库」→「哈尔科夫弹药库」），
--    算法返回的 target_name 经 resolveAlias 映射后再入库。
--    UNIQUE (alias) 保证一个别名只映射一个标准名。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `target_alias` (
  `id`              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
                    NOT NULL COMMENT '主键ID',
  `alias`           VARCHAR(255) NOT NULL COMMENT '别名（输入侧），如「xxx弹药库」',
  `canonical_name`  VARCHAR(255) NOT NULL COMMENT '标准名（统一名），如「哈尔科夫弹药库」',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alias`         (`alias`),
  KEY        `idx_canonical_name`(`canonical_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='目标别名映射（别名 → 标准名）';

-- ---------------------------------------------------------------
-- 4. target_fusion - 目标融合结果表
--    用户在前端勾选多个目标 → 后端聚合该目标全部 target_analysis →
--    调算法 /api/target/fusion → 返回融合结果（综合描述+变化分析+区域融合）→ 入此表。
--    一次融合 = 一行（多个目标合并到一行的 target_names）。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `target_fusion` (
  `id`                    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
                          NOT NULL COMMENT '融合记录ID',
  `target_names`          VARCHAR(1000) NOT NULL
                          COMMENT '参与融合的目标名 JSON 数组字符串，如 ["哈尔科夫弹药库","某机场"]',
  `analysis`              TEXT          COMMENT '算法返回-综合描述（analysis）',
  `difference`            TEXT          COMMENT '算法返回-变化分析（difference）',
  `region_fusion_result`  TEXT          COMMENT '算法返回-区域融合结果文本（region 维度的整体态势）',
  `source_count`          INT NOT NULL DEFAULT 0 COMMENT '参与融合的 target_analysis 记录条数',
  `is_fused`              TINYINT(1) NOT NULL DEFAULT 1
                          COMMENT '该记录是否有效（保留位，恒为1，删除走逻辑删除）',
  `fusion_time`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '融合执行时间',
  `create_time`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_target_names_prefix` (`target_names`(64)) COMMENT '按目标名前缀检索',
  KEY `idx_fusion_time` (`fusion_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='目标融合结果';

-- ---------------------------------------------------------------
-- 5. 别名表初始化数据（示例 — 真实数据由业务方维护）
-- ---------------------------------------------------------------
-- INSERT INTO `target_alias` (`id`, `alias`, `canonical_name`) VALUES
-- ('SEED0000000000000000000000000001', '哈市弹药库',    '哈尔科夫弹药库'),
-- ('SEED0000000000000000000000000002', '哈尔科夫弹药点', '哈尔科夫弹药库');
