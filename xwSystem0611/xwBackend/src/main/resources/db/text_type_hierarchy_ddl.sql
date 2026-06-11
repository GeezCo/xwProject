-- text_type 表增加 parent_id 字段，支持树形分类结构
ALTER TABLE text_type ADD COLUMN parent_id INT DEFAULT NULL COMMENT '父分类ID，NULL表示一级分类';

-- 插入一级分类「开源信息」
INSERT INTO text_type (type_name, parent_id) VALUES ('开源信息', NULL);

-- 将现有分类（id 1~15）挂到「开源信息」下
-- 注意：执行前需确认上面 INSERT 生成的自增 ID，替换下方的 <ID>
-- UPDATE text_type SET parent_id = <ID> WHERE parent_id IS NULL AND type_name != '开源信息';
