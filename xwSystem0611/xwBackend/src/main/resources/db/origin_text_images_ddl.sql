ALTER TABLE origin_text
ADD COLUMN images TEXT DEFAULT NULL COMMENT '图片文件名列表（JSON数组格式，如 ["188_1.jpg","188_2.jpg"]）';
