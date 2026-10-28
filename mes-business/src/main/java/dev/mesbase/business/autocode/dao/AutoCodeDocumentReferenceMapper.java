package dev.mesbase.business.autocode.dao;

import org.apache.ibatis.annotations.Param;

public interface AutoCodeDocumentReferenceMapper {
    int hasWorkorderPrefix(@Param("pattern") String pattern);

    int hasStockDocumentPrefix(@Param("pattern") String pattern, @Param("direction") String direction);
}
