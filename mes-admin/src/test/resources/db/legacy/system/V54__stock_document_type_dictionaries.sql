-- Business dictionary values are the stable StockDocumentType keys; labels and order are presentation only.
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time)
SELECT '入库单类型','stock_document_in_type','0','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='stock_document_in_type');

INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time)
SELECT '出库单类型','stock_document_out_type','0','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='stock_document_out_type');

INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time)
SELECT seed.sort_no,seed.label,seed.business_key,'stock_document_in_type','N','0','migration',CURRENT_TIMESTAMP
FROM (
    SELECT 10 sort_no,'采购入库' label,'PURCHASE_RECEIPT' business_key UNION ALL
    SELECT 20,'生产完工入库','PRODUCTION_RECEIPT' UNION ALL
    SELECT 30,'委外加工入库','OUTSOURCE_RECEIPT' UNION ALL
    SELECT 40,'退料入库','MATERIAL_RETURN_RECEIPT' UNION ALL
    SELECT 50,'返工入库','REWORK_RECEIPT' UNION ALL
    SELECT 60,'销售退货入库','SALES_RETURN_RECEIPT' UNION ALL
    SELECT 70,'不良品入库','DEFECT_RECEIPT' UNION ALL
    SELECT 80,'报废品入库','SCRAP_RECEIPT' UNION ALL
    SELECT 90,'其他入库','OTHER_RECEIPT'
) seed
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data d
                  WHERE d.dict_type='stock_document_in_type' AND d.dict_value=seed.business_key);

INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time)
SELECT seed.sort_no,seed.label,seed.business_key,'stock_document_out_type','N','0','migration',CURRENT_TIMESTAMP
FROM (
    SELECT 10 sort_no,'生产领料出库' label,'MATERIAL_ISSUE' business_key UNION ALL
    SELECT 20,'其他出库','OTHER_ISSUE'
) seed
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data d
                  WHERE d.dict_type='stock_document_out_type' AND d.dict_value=seed.business_key);
