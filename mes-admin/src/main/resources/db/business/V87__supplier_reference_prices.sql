-- 供应商编码在当前独立业务库内唯一；不叠加工厂字段。
CREATE TABLE purchase_supplier (
 id bigint NOT NULL AUTO_INCREMENT COMMENT '供应商主键',
 code varchar(64) NOT NULL COMMENT '本业务库内唯一供应商编码',
 name varchar(200) NOT NULL COMMENT '供应商当前名称',
 contact_person varchar(100) DEFAULT NULL COMMENT '供应商联系人',
 contact_phone varchar(64) DEFAULT NULL COMMENT '供应商联系电话',
 address varchar(500) DEFAULT NULL COMMENT '供应商联系地址',
 status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '供应商状态：ENABLED启用或DISABLED停用',
 remark varchar(500) DEFAULT NULL COMMENT '供应商备注',
 created_by bigint NOT NULL COMMENT '创建人的系统用户主键',
 created_at datetime(6) NOT NULL COMMENT '供应商创建时间',
 updated_by bigint NOT NULL COMMENT '最后修改人的系统用户主键',
 updated_at datetime(6) NOT NULL COMMENT '供应商最后修改时间',
 PRIMARY KEY(id), UNIQUE KEY uk_purchase_supplier_code(code), KEY idx_purchase_supplier_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本地采购供应商主数据';
CREATE TABLE purchase_reference_price (
 id bigint NOT NULL AUTO_INCREMENT COMMENT '追加式参考报价主键，同日修订按此主键倒序确定',
 supplier_id bigint NOT NULL COMMENT '报价所属供应商主键',
 material_id bigint NOT NULL COMMENT '报价所属统一物料主键',
 unit_id bigint NOT NULL COMMENT '报价采用的物料基本单位主键快照',
 unit_price decimal(20,6) NOT NULL COMMENT '每基本单位参考单价，最多六位小数，允许有依据的零价',
 currency varchar(3) NOT NULL COMMENT '报价币种，V1使用CNY人民币',
 effective_date date NOT NULL COMMENT '报价生效业务日期，与录入时间独立',
 created_by bigint NOT NULL COMMENT '报价录入人的系统用户主键',
 created_at datetime(6) NOT NULL COMMENT '报价实际录入时间',
 remark varchar(500) DEFAULT NULL COMMENT '调价说明，零价时必填',
 PRIMARY KEY(id), KEY idx_reference_price_effective(supplier_id,material_id,effective_date,id), KEY idx_reference_price_material(material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商物料参考报价历史，只追加不覆盖';
ALTER TABLE wm_stock_document
 ADD COLUMN supplier_id bigint DEFAULT NULL COMMENT '正式供应商关联，旧V86单据可空且不按名称猜配',
 ADD COLUMN business_date date DEFAULT NULL COMMENT '采购入库业务日期，旧单据保持为空',
 ADD COLUMN currency varchar(3) DEFAULT NULL COMMENT '采购成交币种，V1为CNY，旧单据不补猜',
 ADD KEY idx_stock_document_supplier(supplier_id);
ALTER TABLE wm_stock_document_line
 ADD COLUMN reference_price_id bigint DEFAULT NULL COMMENT '保存时所选参考报价主键，可无报价且不随调价改写',
 ADD COLUMN reference_unit_price decimal(20,6) DEFAULT NULL COMMENT '保存时参考单价快照，不是实际成交价',
 ADD COLUMN unit_price decimal(20,6) DEFAULT NULL COMMENT '实际成交基本单位单价，旧单据可空',
 ADD COLUMN amount decimal(32,2) DEFAULT NULL COMMENT '实际成交行金额，由数量乘成交价并四舍五入至分，非库存成本';
