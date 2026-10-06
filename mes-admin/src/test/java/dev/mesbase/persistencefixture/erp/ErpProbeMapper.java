package dev.mesbase.persistencefixture.erp;

import org.apache.ibatis.annotations.Select;

public interface ErpProbeMapper { @Select("SELECT label FROM marker") String label(); }
