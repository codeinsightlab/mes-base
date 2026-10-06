package dev.mesbase.invalidfixture;

import org.apache.ibatis.annotations.Select;

public interface UnownedMapper { @Select("SELECT 1") int value(); }
