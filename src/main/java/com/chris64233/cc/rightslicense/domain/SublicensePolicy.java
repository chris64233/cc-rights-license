package com.chris64233.cc.rightslicense.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 转授权策略：授权方声明"持权方可以再向他人授予什么"。
 *
 * @param sublicensable  是否允许转授权
 * @param maxDepth       允许的最大绝对层级深度：根授权为 1，其直接下级为 2，以此类推；不允许转授权时为 null
 * @param subTerritories 允许向下转授的地域上限（null 表示与本级地域相同）
 * @param subMedia       允许向下转授的媒介上限（null 表示与本级媒介相同）
 * @param subStartDate   允许向下转授的起始日期下限（null 表示与本级起始日相同）
 * @param subEndDate     允许向下转授的截止日期上限（null 表示与本级截止日相同）
 */
public record SublicensePolicy(boolean sublicensable,
                               Integer maxDepth,
                               List<String> subTerritories,
                               List<String> subMedia,
                               LocalDate subStartDate,
                               LocalDate subEndDate) {

    public static SublicensePolicy forbidden() {
        return new SublicensePolicy(false, null, null, null, null, null);
    }
}
