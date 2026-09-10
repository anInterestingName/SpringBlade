-- REQ-2026-002 标签分类初始业务数据
-- 适用范围：blade_tag_category、blade_tag 已创建后的租户级手工导入。
-- 使用前修改 @tenant_id；脚本不使用临时表，不更新或删除已有同编码数据。
-- 首次执行保留 ID 偏移量为 0；向其他租户复制时必须设置新的偏移量。

-- tenant_id 列为 utf8mb4_general_ci，显式声明变量排序规则，
-- 避免 MySQL 8.0 下用户变量默认使用 utf8mb4_0900_ai_ci 导致“Illegal mix of collations”。
SET @tenant_id = '000000' COLLATE utf8mb4_general_ci;
SET @seed_time = NOW();
SET @category_id_offset = 0;
SET @tag_id_offset = 0;

START TRANSACTION;

INSERT INTO blade_tag_category
  (id, category_code, category_name, selection_mode, max_select_count, sort, remark,
   lock_version, status, tenant_id, create_user, create_dept, create_time,
   update_user, update_time, is_deleted)
VALUES
  (220260910200000001 + @category_id_offset, 'content_type', '内容类型', 1, 1, 10,
   '描述内容最终呈现的主要类型，每条内容选择一个。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910200000002 + @category_id_offset, 'visual_style', '视觉风格', 2, 4, 20,
   '描述整体视觉语言与设计风格。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910200000003 + @category_id_offset, 'medium', '表现媒介', 2, 3, 30,
   '描述画面采用的主要创作或呈现媒介。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910200000004 + @category_id_offset, 'composition', '构图方式', 2, 3, 40,
   '描述主体组织、视角和画面布局方式。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910200000005 + @category_id_offset, 'lighting', '光线特征', 2, 3, 50,
   '描述画面的主要光源、光质和明暗关系。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910200000006 + @category_id_offset, 'color', '色彩特征', 2, 3, 60,
   '描述画面的主要色调、饱和度和配色关系。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910200000007 + @category_id_offset, 'business_scene', '业务场景', 2, 3, 70,
   '描述内容适用的行业或业务使用场景。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910200000008 + @category_id_offset, 'text_feature', '文字特征', 1, 1, 80,
   '描述画面中文字内容的数量和呈现形式。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910200000009 + @category_id_offset, 'mood', '情绪氛围', 2, 3, 90,
   '描述画面传递的主要情绪和氛围。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0)
ON DUPLICATE KEY UPDATE id = id;

SET @composition_id = (
  SELECT id FROM blade_tag_category
  WHERE tenant_id COLLATE utf8mb4_general_ci = @tenant_id AND category_code = 'composition' AND is_deleted = 0 LIMIT 1
);
SET @lighting_id = (
  SELECT id FROM blade_tag_category
  WHERE tenant_id COLLATE utf8mb4_general_ci = @tenant_id AND category_code = 'lighting' AND is_deleted = 0 LIMIT 1
);
SET @color_id = (
  SELECT id FROM blade_tag_category
  WHERE tenant_id COLLATE utf8mb4_general_ci = @tenant_id AND category_code = 'color' AND is_deleted = 0 LIMIT 1
);

-- 以下标签均为根节点：parent_id=0、ancestors='0'、depth=1。

-- 构图方式：多选，最多三项
INSERT INTO blade_tag
  (id, category_id, parent_id, ancestors, depth, tag_code, tag_name, sort, remark,
   lock_version, status, tenant_id, create_user, create_dept, create_time,
   update_user, update_time, is_deleted)
VALUES
  (220260910210000301 + @tag_id_offset, @composition_id, 0, '0', 1, 'centered', '居中', 10, '主要主体位于画面中心区域。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000302 + @tag_id_offset, @composition_id, 0, '0', 1, 'symmetrical', '对称', 20, '画面元素沿中心轴形成明显对称关系。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000303 + @tag_id_offset, @composition_id, 0, '0', 1, 'asymmetrical', '非对称', 30, '通过不对称元素形成视觉平衡。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000304 + @tag_id_offset, @composition_id, 0, '0', 1, 'diagonal', '对角线', 40, '主体或视觉动线沿对角方向展开。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000305 + @tag_id_offset, @composition_id, 0, '0', 1, 'top_down', '俯视', 50, '从主体上方向下观察和组织画面。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000306 + @tag_id_offset, @composition_id, 0, '0', 1, 'close_up', '特写', 60, '以近距离视角突出主体局部和细节。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000307 + @tag_id_offset, @composition_id, 0, '0', 1, 'negative_space', '留白', 70, '保留较大空白区域以突出主体或文字。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000308 + @tag_id_offset, @composition_id, 0, '0', 1, 'multi_column', '多栏', 80, '将内容划分为多个纵向或横向栏区。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000309 + @tag_id_offset, @composition_id, 0, '0', 1, 'rule_of_thirds', '三分法', 90, '主体沿三分线或交点进行布局。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000310 + @tag_id_offset, @composition_id, 0, '0', 1, 'flat_lay', '平铺', 100, '从上方呈现平面排列的多个物体。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 光线特征：多选，最多三项
INSERT INTO blade_tag
  (id, category_id, parent_id, ancestors, depth, tag_code, tag_name, sort, remark,
   lock_version, status, tenant_id, create_user, create_dept, create_time,
   update_user, update_time, is_deleted)
VALUES
  (220260910210000401 + @tag_id_offset, @lighting_id, 0, '0', 1, 'natural_light', '自然光', 10, '主要使用太阳光或环境自然光。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000402 + @tag_id_offset, @lighting_id, 0, '0', 1, 'studio_light', '棚拍光', 20, '使用可控摄影灯形成清晰商业照明。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000403 + @tag_id_offset, @lighting_id, 0, '0', 1, 'backlight', '逆光', 30, '主要光源位于主体后方并形成轮廓或透光效果。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000404 + @tag_id_offset, @lighting_id, 0, '0', 1, 'soft_light', '柔光', 40, '光线过渡柔和，阴影边缘不明显。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000405 + @tag_id_offset, @lighting_id, 0, '0', 1, 'hard_light', '硬光', 50, '光线方向明确，阴影边缘清晰且反差较强。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000406 + @tag_id_offset, @lighting_id, 0, '0', 1, 'neon_light', '霓虹光', 60, '使用高纯度彩色发光体形成环境照明。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000407 + @tag_id_offset, @lighting_id, 0, '0', 1, 'cinematic_light', '电影光', 70, '通过方向性、层次和色温对比形成电影画面感。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000408 + @tag_id_offset, @lighting_id, 0, '0', 1, 'low_key', '低调光', 80, '暗部占比较高，仅保留有限高光和照明区域。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000409 + @tag_id_offset, @lighting_id, 0, '0', 1, 'high_key', '高调光', 90, '整体明亮、阴影较少且反差较低。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000410 + @tag_id_offset, @lighting_id, 0, '0', 1, 'golden_hour', '黄金时刻', 100, '使用日出或日落附近的暖色低角度光线。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 色彩特征：多选，最多三项
INSERT INTO blade_tag
  (id, category_id, parent_id, ancestors, depth, tag_code, tag_name, sort, remark,
   lock_version, status, tenant_id, create_user, create_dept, create_time,
   update_user, update_time, is_deleted)
VALUES
  (220260910210000501 + @tag_id_offset, @color_id, 0, '0', 1, 'high_saturation', '高饱和', 10, '画面主要颜色鲜艳且纯度较高。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000502 + @tag_id_offset, @color_id, 0, '0', 1, 'low_saturation', '低饱和', 20, '画面主要颜色柔和、灰度较高。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000503 + @tag_id_offset, @color_id, 0, '0', 1, 'monochrome', '黑白', 30, '画面以黑、白和灰阶为主要颜色。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000504 + @tag_id_offset, @color_id, 0, '0', 1, 'warm_tone', '暖色调', 40, '画面以红、橙、黄等暖色为主。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000505 + @tag_id_offset, @color_id, 0, '0', 1, 'cool_tone', '冷色调', 50, '画面以蓝、青、紫等冷色为主。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000506 + @tag_id_offset, @color_id, 0, '0', 1, 'contrasting_colors', '撞色', 60, '使用差异明显的颜色形成强烈视觉对比。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000507 + @tag_id_offset, @color_id, 0, '0', 1, 'pastel_colors', '粉彩', 70, '使用明度较高、饱和度较低的柔和颜色。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000508 + @tag_id_offset, @color_id, 0, '0', 1, 'dark_palette', '深色系', 80, '整体以低明度颜色和深色背景为主。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000509 + @tag_id_offset, @color_id, 0, '0', 1, 'bright_palette', '明亮色系', 90, '整体以高明度颜色和明亮背景为主。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000510 + @tag_id_offset, @color_id, 0, '0', 1, 'red_accent', '红色点缀', 100, '以红色作为局部重点和视觉引导。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0)
ON DUPLICATE KEY UPDATE id = id;

SET @content_type_id = (
  SELECT id FROM blade_tag_category
  WHERE tenant_id COLLATE utf8mb4_general_ci = @tenant_id AND category_code = 'content_type' AND is_deleted = 0 LIMIT 1
);
SET @visual_style_id = (
  SELECT id FROM blade_tag_category
  WHERE tenant_id COLLATE utf8mb4_general_ci = @tenant_id AND category_code = 'visual_style' AND is_deleted = 0 LIMIT 1
);
SET @medium_id = (
  SELECT id FROM blade_tag_category
  WHERE tenant_id COLLATE utf8mb4_general_ci = @tenant_id AND category_code = 'medium' AND is_deleted = 0 LIMIT 1
);
SET @business_scene_id = (
  SELECT id FROM blade_tag_category
  WHERE tenant_id COLLATE utf8mb4_general_ci = @tenant_id AND category_code = 'business_scene' AND is_deleted = 0 LIMIT 1
);
SET @text_feature_id = (
  SELECT id FROM blade_tag_category
  WHERE tenant_id COLLATE utf8mb4_general_ci = @tenant_id AND category_code = 'text_feature' AND is_deleted = 0 LIMIT 1
);
SET @mood_id = (
  SELECT id FROM blade_tag_category
  WHERE tenant_id COLLATE utf8mb4_general_ci = @tenant_id AND category_code = 'mood' AND is_deleted = 0 LIMIT 1
);

-- 内容类型：单选
INSERT INTO blade_tag
  (id, category_id, parent_id, ancestors, depth, tag_code, tag_name, sort, remark,
   lock_version, status, tenant_id, create_user, create_dept, create_time,
   update_user, update_time, is_deleted)
VALUES
  (220260910210000001 + @tag_id_offset, @content_type_id, 0, '0', 1, 'photo_image', '摄影图片', 10, '以真实摄影画面为主要呈现形式。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000002 + @tag_id_offset, @content_type_id, 0, '0', 1, 'illustration', '插画', 20, '以手绘或数字绘制图像为主要内容。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000003 + @tag_id_offset, @content_type_id, 0, '0', 1, 'three_d_art', '3D作品', 30, '以三维建模或立体视觉为主要内容。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000004 + @tag_id_offset, @content_type_id, 0, '0', 1, 'ui_design', 'UI界面', 40, '以应用、网页或设备界面为主要内容。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000005 + @tag_id_offset, @content_type_id, 0, '0', 1, 'poster', '海报', 50, '以单页视觉传播和主题表达为主要内容。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000006 + @tag_id_offset, @content_type_id, 0, '0', 1, 'infographic', '信息图', 60, '以图表、图形和文字组织信息。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000007 + @tag_id_offset, @content_type_id, 0, '0', 1, 'ecommerce_image', '电商图片', 70, '用于商品展示、详情或促销的商业图片。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000008 + @tag_id_offset, @content_type_id, 0, '0', 1, 'architecture_visual', '建筑视觉', 80, '以建筑空间、室内或景观设计为主要内容。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 视觉风格：多选，最多四项
INSERT INTO blade_tag
  (id, category_id, parent_id, ancestors, depth, tag_code, tag_name, sort, remark,
   lock_version, status, tenant_id, create_user, create_dept, create_time,
   update_user, update_time, is_deleted)
VALUES
  (220260910210000101 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'realistic', '写实', 10, '强调真实比例、材质、光影和细节。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000102 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'minimalist', '极简', 20, '使用较少元素、克制配色和清晰留白。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000103 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'vintage', '复古', 30, '呈现特定年代的造型、色彩或质感。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000104 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'cyberpunk', '赛博朋克', 40, '使用高科技都市、霓虹和反乌托邦视觉语言。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000105 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'watercolor_style', '水彩风格', 50, '呈现透明晕染、柔和边缘和纸面水彩质感。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000106 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'ink_style', '水墨风格', 60, '呈现墨色层次、笔触和东方留白。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000107 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'anime', '动漫', 70, '使用动画或漫画式人物、线条和色块。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000108 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'collage', '拼贴', 80, '组合照片、文字、纹理或图形形成层叠画面。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000109 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'editorial_design', '编辑设计', 90, '使用杂志化版式、强标题和图文编排。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000110 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'retro_print', '复古印刷', 100, '呈现网点、套色偏移、纸张和旧印刷质感。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000111 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'surrealism', '超现实', 110, '通过不合常理的组合、尺度或空间形成梦境感。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000112 + @tag_id_offset, @visual_style_id, 0, '0', 1, 'flat_design', '扁平设计', 120, '以简化形状、纯色块和弱立体感表达内容。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 表现媒介：多选，最多三项
INSERT INTO blade_tag
  (id, category_id, parent_id, ancestors, depth, tag_code, tag_name, sort, remark,
   lock_version, status, tenant_id, create_user, create_dept, create_time,
   update_user, update_time, is_deleted)
VALUES
  (220260910210000201 + @tag_id_offset, @medium_id, 0, '0', 1, 'photography', '摄影', 10, '通过相机或摄影语言形成画面。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000202 + @tag_id_offset, @medium_id, 0, '0', 1, 'oil_painting', '油画', 20, '呈现油画颜料、笔触和厚重色层。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000203 + @tag_id_offset, @medium_id, 0, '0', 1, 'watercolor', '水彩', 30, '以水彩透明色层和自然晕染表现。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000204 + @tag_id_offset, @medium_id, 0, '0', 1, 'ink_wash', '水墨', 40, '以墨色浓淡、笔触和宣纸质感表现。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000205 + @tag_id_offset, @medium_id, 0, '0', 1, 'paper_cut', '纸雕', 50, '以纸张裁切、折叠和层叠结构表现。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000206 + @tag_id_offset, @medium_id, 0, '0', 1, 'clay_art', '黏土', 60, '以黏土或软陶材质形成手作立体效果。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000207 + @tag_id_offset, @medium_id, 0, '0', 1, 'pixel_art', '像素', 70, '使用可见像素网格和低分辨率造型。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000208 + @tag_id_offset, @medium_id, 0, '0', 1, 'three_d_render', '3D渲染', 80, '通过三维建模、材质和渲染形成画面。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000209 + @tag_id_offset, @medium_id, 0, '0', 1, 'vector_graphic', '矢量图形', 90, '以几何路径、清晰边缘和可缩放图形表现。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000210 + @tag_id_offset, @medium_id, 0, '0', 1, 'pencil_sketch', '铅笔素描', 100, '以铅笔线条、排线和明暗关系表现。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 业务场景：多选，最多三项
INSERT INTO blade_tag
  (id, category_id, parent_id, ancestors, depth, tag_code, tag_name, sort, remark,
   lock_version, status, tenant_id, create_user, create_dept, create_time,
   update_user, update_time, is_deleted)
VALUES
  (220260910210000601 + @tag_id_offset, @business_scene_id, 0, '0', 1, 'ecommerce', '电商', 10, '用于商品展示、销售转化和促销传播。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000602 + @tag_id_offset, @business_scene_id, 0, '0', 1, 'education', '教育', 20, '用于课程、知识讲解、培训和学习内容。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000603 + @tag_id_offset, @business_scene_id, 0, '0', 1, 'technology', '科技', 30, '用于技术产品、数字服务和创新主题。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000604 + @tag_id_offset, @business_scene_id, 0, '0', 1, 'food_beverage', '餐饮', 40, '用于食品、饮品、餐厅和菜单内容。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000605 + @tag_id_offset, @business_scene_id, 0, '0', 1, 'travel', '旅游', 50, '用于目的地、景点、酒店和旅行服务。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000606 + @tag_id_offset, @business_scene_id, 0, '0', 1, 'social_media', '社交媒体', 60, '用于社交平台内容发布和互动传播。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000607 + @tag_id_offset, @business_scene_id, 0, '0', 1, 'brand_campaign', '品牌营销', 70, '用于品牌形象、广告活动和市场传播。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000608 + @tag_id_offset, @business_scene_id, 0, '0', 1, 'finance', '金融', 80, '用于银行、投资、保险和财经信息。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000609 + @tag_id_offset, @business_scene_id, 0, '0', 1, 'healthcare', '医疗健康', 90, '用于医疗服务、健康管理和生命科学内容。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000610 + @tag_id_offset, @business_scene_id, 0, '0', 1, 'real_estate', '房地产', 100, '用于住宅、商业地产、空间和项目推广。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 文字特征：单选
INSERT INTO blade_tag
  (id, category_id, parent_id, ancestors, depth, tag_code, tag_name, sort, remark,
   lock_version, status, tenant_id, create_user, create_dept, create_time,
   update_user, update_time, is_deleted)
VALUES
  (220260910210000701 + @tag_id_offset, @text_feature_id, 0, '0', 1, 'no_text', '无文字', 10, '画面中没有可识别的文字内容。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000702 + @tag_id_offset, @text_feature_id, 0, '0', 1, 'title_only', '仅标题', 20, '画面以一处主要标题文字为主。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000703 + @tag_id_offset, @text_feature_id, 0, '0', 1, 'sparse_text', '少量文字', 30, '画面包含少量标题、说明或短句。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000704 + @tag_id_offset, @text_feature_id, 0, '0', 1, 'dense_text', '信息密集', 40, '画面包含较多文字、数据或说明信息。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000705 + @tag_id_offset, @text_feature_id, 0, '0', 1, 'ui_text', 'UI文本', 50, '文字主要作为界面控件、导航或状态内容出现。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0)
ON DUPLICATE KEY UPDATE id = id;

-- 情绪氛围：多选，最多三项
INSERT INTO blade_tag
  (id, category_id, parent_id, ancestors, depth, tag_code, tag_name, sort, remark,
   lock_version, status, tenant_id, create_user, create_dept, create_time,
   update_user, update_time, is_deleted)
VALUES
  (220260910210000801 + @tag_id_offset, @mood_id, 0, '0', 1, 'healing', '治愈', 10, '传递温暖、安慰和舒缓感受。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000802 + @tag_id_offset, @mood_id, 0, '0', 1, 'serious', '严肃', 20, '传递正式、克制和需要认真对待的感受。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000803 + @tag_id_offset, @mood_id, 0, '0', 1, 'dreamy', '梦幻', 30, '传递轻盈、朦胧和非现实的想象感。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000804 + @tag_id_offset, @mood_id, 0, '0', 1, 'tense', '紧张', 40, '传递压力、冲突、速度或危险感。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000805 + @tag_id_offset, @mood_id, 0, '0', 1, 'luxurious', '奢华', 50, '传递精致、昂贵和高品质感受。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000806 + @tag_id_offset, @mood_id, 0, '0', 1, 'lively', '活泼', 60, '传递鲜明、积极和富有动感的感受。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000807 + @tag_id_offset, @mood_id, 0, '0', 1, 'calm', '宁静', 70, '传递平稳、安静和低刺激的感受。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000808 + @tag_id_offset, @mood_id, 0, '0', 1, 'mysterious', '神秘', 80, '传递未知、隐晦和探索感受。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000809 + @tag_id_offset, @mood_id, 0, '0', 1, 'romantic', '浪漫', 90, '传递柔美、亲密和情感化氛围。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0),
  (220260910210000810 + @tag_id_offset, @mood_id, 0, '0', 1, 'nostalgic', '怀旧', 100, '传递年代记忆、旧物和时光感。', 0, 1, @tenant_id, NULL, NULL, @seed_time, NULL, @seed_time, 0)
ON DUPLICATE KEY UPDATE id = id;

COMMIT;

-- 导入核对：新环境应返回九行，标签总数合计 85。
SELECT category.category_code,
       category.category_name,
       category.selection_mode,
       category.max_select_count,
       category.status,
       COUNT(tag.id) AS tag_count
FROM blade_tag_category category
LEFT JOIN blade_tag tag
  ON tag.tenant_id COLLATE utf8mb4_general_ci = category.tenant_id COLLATE utf8mb4_general_ci
 AND tag.category_id = category.id
 AND tag.is_deleted = 0
WHERE category.tenant_id COLLATE utf8mb4_general_ci = @tenant_id
  AND category.is_deleted = 0
  AND category.category_code IN (
    'content_type', 'visual_style', 'medium', 'composition', 'lighting',
    'color', 'business_scene', 'text_feature', 'mood'
  )
GROUP BY category.id,
         category.category_code,
         category.category_name,
         category.selection_mode,
         category.max_select_count,
         category.status,
         category.sort
ORDER BY category.sort, category.id;
