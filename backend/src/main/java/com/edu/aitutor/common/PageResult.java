package com.edu.aitutor.common;

import lombok.Data;
import java.util.List;

/**
 * 通用分页结果包装。
 */
@Data
public class PageResult<T> {
    /** 总条数 */
    private Long total;
    /** 当前页数据 */
    private List<T> list;

    public PageResult(Long total, List<T> list) {
        this.total = total;
        this.list = list;
    }
}
