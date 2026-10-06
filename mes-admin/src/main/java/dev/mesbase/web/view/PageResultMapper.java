package dev.mesbase.web.view;

import com.github.pagehelper.PageInfo;
import dev.mesbase.common.core.page.TableDataInfo;
import java.util.List;
import java.util.function.Function;

/** Preserve the existing PageHelper total before converting the HTTP rows. */
public final class PageResultMapper {
    private PageResultMapper() {
    }

    public static <S, R> TableDataInfo map(List<S> rows, Function<S, R> converter) {
        long total = new PageInfo<>(rows).getTotal();
        TableDataInfo result = new TableDataInfo();
        result.setCode(200);
        result.setMsg("查询成功");
        result.setTotal(total);
        result.setRows(rows.stream().map(converter).toList());
        return result;
    }
}
