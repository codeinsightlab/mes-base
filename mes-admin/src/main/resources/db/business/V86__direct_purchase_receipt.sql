-- 直接采购入库V1：供应商作为单据快照，不引入采购订单或供应商主数据表。
ALTER TABLE wm_stock_document
  ADD COLUMN supplier_name varchar(200) DEFAULT NULL COMMENT '直接采购入库的供应商名称快照，审核时必填',
  ADD COLUMN supplier_document_no varchar(128) DEFAULT NULL COMMENT '供应商送货单号或交货凭据编号，不代表采购订单关联';
