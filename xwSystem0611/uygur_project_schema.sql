
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `extraction_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `extraction_result` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `origin_text_id` int(11) NOT NULL,
  `extraction_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `model` varchar(50) DEFAULT 'GLM-5',
  `total_events` int(11) DEFAULT '0',
  `events_json` longtext,
  `status` varchar(20) DEFAULT 'completed',
  `error_message` text,
  `labels_json` text COMMENT '分类标签JSON数组',
  `entities_json` longtext COMMENT 'å®žä½“JSON',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_origin_text_id` (`origin_text_id`)
) ENGINE=InnoDB AUTO_INCREMENT=216 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fusion_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fusion_report` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '融合报告ID',
  `title` varchar(200) NOT NULL COMMENT '报告标题',
  `summary` text COMMENT '报告摘要',
  `timeline` json DEFAULT NULL COMMENT '事件时间线',
  `content` text COMMENT '详细内容',
  `entities` json DEFAULT NULL COMMENT '关键实体',
  `labels` json DEFAULT NULL COMMENT '综合标签',
  `source_ids` varchar(100) DEFAULT NULL COMMENT '参与融合的报文ID列表',
  `model_used` varchar(50) DEFAULT NULL COMMENT '使用的大模型',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COMMENT='报文融合报告表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `origin_text`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `origin_text` (
  `sid` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `content` varchar(7000) DEFAULT NULL,
  `times` varchar(255) DEFAULT NULL,
  `type` int(11) DEFAULT NULL,
  `extend1` varchar(255) DEFAULT NULL,
  `extend2` varchar(255) DEFAULT NULL,
  `extend3` varchar(255) DEFAULT NULL,
  `is_extracted` tinyint(1) DEFAULT '0',
  `modal_type` varchar(20) DEFAULT '文字报' COMMENT '报文模态类型',
  PRIMARY KEY (`sid`) USING BTREE,
  KEY `type_id` (`type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=63250 DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `text_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `text_type` (
  `sid` int(11) NOT NULL AUTO_INCREMENT,
  `id` int(11) DEFAULT NULL,
  `type_name` varchar(255) DEFAULT NULL,
  `extend1` varchar(255) DEFAULT NULL,
  `extend2` varchar(255) DEFAULT NULL,
  `extend3` varchar(255) DEFAULT NULL,
  `parent_id` int(11) DEFAULT NULL COMMENT '父分类ID，NULL表示一级分类',
  PRIMARY KEY (`sid`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=77 DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

