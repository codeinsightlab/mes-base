package dev.mesbase.business.basic.dao;

import org.apache.ibatis.annotations.*;

public interface BomPlanReceiptMapper {
    @Insert("INSERT INTO material_bom_plan_receipt(request_id,actor_id,plan_hash) VALUES(#{id},#{actor},#{hash}) ON DUPLICATE KEY UPDATE request_id=request_id")
    int claim(@Param("id") String id, @Param("actor") long actor, @Param("hash") String hash);

    @Select("SELECT root_id FROM material_bom_plan_receipt WHERE request_id=#{id} AND actor_id=#{actor} AND plan_hash=#{hash} FOR UPDATE")
    Long replay(@Param("id") String id, @Param("actor") long actor, @Param("hash") String hash);

    @Update("UPDATE material_bom_plan_receipt SET root_id=#{root} WHERE request_id=#{id} AND actor_id=#{actor} AND plan_hash=#{hash} AND root_id IS NULL")
    int finish(@Param("id") String id, @Param("actor") long actor, @Param("hash") String hash,
               @Param("root") long root);
}
