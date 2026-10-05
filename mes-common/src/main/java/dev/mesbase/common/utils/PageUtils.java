package dev.mesbase.common.utils;

import com.github.pagehelper.PageHelper;
import dev.mesbase.common.core.page.PageDomain;
import dev.mesbase.common.core.page.TableSupport;
import dev.mesbase.common.utils.sql.SqlUtil;

/**
 * 分页工具类
 * 
 * @author ruoyi
 */
public class PageUtils extends PageHelper
{
    /**
     * 设置请求分页数据
     */
    public static void startPage()
    {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        int page=pageDomain.getPageNum()==null?1:pageDomain.getPageNum();
        int size=pageDomain.getPageSize()==null?20:pageDomain.getPageSize();
        if(page<1 || size<1 || size>100) throw new dev.mesbase.common.exception.ServiceException("分页范围无效");
        PageHelper.startPage(page,size).setReasonable(false);
    }

    /**
     * 清理分页的线程变量
     */
    public static void clearPage()
    {
        PageHelper.clearPage();
    }
}
