package com.chris64233.cc.rightslicense.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 授权的转授权策略声明（值对象）。
 *
 * @param sublicensable 是否允许转授权
 * @param maxLevels     允许向下转授权的层级数（相对于本授权；null 表示不限层级）
 * @param territories   允许转授权的地域范围（空列表表示不额外限制，即等于本授权地域）
 * @param media         允许转授权的媒介范围（空列表表示不额外限制）
 * @param startBound    转授权期限下界（null 表示不额外限制）
 * @param endBound      转授权期限上界（null 表示不额外限制）
 */
public record SublicensePolicy(boolean sublicensable,
                               Integer maxLevels,
                               List<String> territories,
                               List<String> media,
                               LocalDate startBound,
                               LocalDate endBound) {

    public SublicensePolicy {
        territories = territories != null ? List.copyOf(territories) : List.of();
        media = media != null ? List.copyOf(media) : List.of();
    }

    public static SublicensePolicy disabled() {
        return new SublicensePolicy(false, null, List.of(), List.of(), null, null);
    }
}
