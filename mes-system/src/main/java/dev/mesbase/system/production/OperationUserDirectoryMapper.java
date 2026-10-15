package dev.mesbase.system.production;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Shared system identity projection; no passwords or private user fields.
 */
public interface OperationUserDirectoryMapper {
    record UserOption(Long id, String name) {
    }

    @Select("""
            <script>
            SELECT u.user_id AS id, u.nick_name AS name FROM sys_user u
            WHERE u.status='0' AND u.del_flag='0'
            <if test="id != null">AND u.user_id=#{id}</if>
            <if test="keyword != null">AND (u.nick_name LIKE CONCAT('%',#{keyword},'%') OR u.user_name LIKE CONCAT('%',#{keyword},'%'))</if>
            <if test="userIds != null">AND u.user_id IN
            <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach></if>
            AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id
              JOIN sys_role_menu rm ON rm.role_id=r.role_id
              WHERE ur.user_id=u.user_id AND r.status='0' AND r.del_flag='0'
              AND ((r.kind='PLATFORM' AND r.factory_id IS NULL) OR (r.kind='FACTORY' AND r.factory_id=#{factory}))
              AND rm.menu_id IN
              <foreach collection="menuIds" item="menu" open="(" separator="," close=")">#{menu}</foreach>)
            ORDER BY u.user_id
            </script>
            """)
    List<UserOption> select(@Param("factory") String factory, @Param("id") Long id,
                            @Param("keyword") String keyword, @Param("menuIds") List<Long> menuIds,
                            @Param("userIds") List<Long> userIds);
}
