package dev.mesbase.system.mapper;

import dev.mesbase.system.domain.SysAttachment;

import java.util.List;

/**
 * 附件Mapper接口
 * 
 * @author yinjinlu
 * @date 2022-07-26
 */
public interface SysAttachmentMapper 
{
    SysAttachment findByFileId(String fileId);
    /**
     * 查询附件
     * 
     * @param attachmentId 附件主键
     * @return 附件
     */
    public SysAttachment selectSysAttachmentByAttachmentId(Long attachmentId);

    /**
     * 查询附件列表
     * 
     * @param sysAttachment 附件
     * @return 附件集合
     */
    public List<SysAttachment> selectSysAttachmentList(SysAttachment sysAttachment);

    /**
     * 新增附件
     * 
     * @param sysAttachment 附件
     * @return 结果
     */
    public int insertSysAttachment(SysAttachment sysAttachment);

    /**
     * 修改附件
     * 
     * @param sysAttachment 附件
     * @return 结果
     */
    public int updateSysAttachment(SysAttachment sysAttachment);

    /**
     * 删除附件
     * 
     * @param attachmentId 附件主键
     * @return 结果
     */
    public int deleteSysAttachmentByAttachmentId(Long attachmentId);

    /**
     * 批量删除附件
     * 
     * @param attachmentIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSysAttachmentByAttachmentIds(Long[] attachmentIds);
    @org.apache.ibatis.annotations.Update("UPDATE sys_attachment SET source_doc_id=#{notice} WHERE file_id=#{file} AND resource='notice-media'")
    int bindNotice(@org.apache.ibatis.annotations.Param("notice")Long notice,@org.apache.ibatis.annotations.Param("file")String file);
}
