-- 物料组成版本与直接组件；分类父子关系继续使用原分类表。
CREATE TABLE material_bom (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物料组成版本本地主键',
 code VARCHAR(64) NOT NULL COMMENT '物料组成业务编码',
 version VARCHAR(32) NOT NULL COMMENT '明确选用的版本标识',
 product_id BIGINT NOT NULL COMMENT '本版本产出的物料主键',
 base_qty DECIMAL(20,6) NOT NULL COMMENT '按产出基本单位计量的基准产出数量',
 unit_id BIGINT NOT NULL COMMENT '产出物料基本单位快照',
 status VARCHAR(16) NOT NULL COMMENT '草稿发布或停用状态',
 revision BIGINT NOT NULL COMMENT '并发修改修订号',
 remark VARCHAR(500) NULL COMMENT '用料版本业务备注',
 created_by BIGINT NOT NULL COMMENT '创建系统用户主键',
 created_at DATETIME NOT NULL COMMENT '创建时间',
 published_at DATETIME NULL COMMENT '首次发布时间',
 PRIMARY KEY (id),
 INDEX idx_bom_product (product_id,status),
 INDEX idx_bom_code_version (code,version)
) COMMENT='物料组成版本，以直接组件为用量权威';

CREATE TABLE material_bom_item (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '组成组件行本地主键',
 bom_id BIGINT NOT NULL COMMENT '所属组成版本主键',
 material_id BIGINT NOT NULL COMMENT '直接使用的组件物料主键',
 quantity DECIMAL(20,6) NOT NULL COMMENT '对应本版基准产出所需的组件基本单位数量',
 unit_id BIGINT NOT NULL COMMENT '组件物料基本单位快照',
 child_bom_id BIGINT NULL COMMENT '明确选定的组件子组成版本，空值表示本层叶项',
 sort INT NOT NULL COMMENT '组件显示顺序',
 PRIMARY KEY (id),
 INDEX idx_bom_item_parent (bom_id,sort),
 INDEX idx_bom_item_material (material_id),
 INDEX idx_bom_item_child (child_bom_id)
) COMMENT='组成直接组件及明确子版本引用，多阶由引用路径展开';
