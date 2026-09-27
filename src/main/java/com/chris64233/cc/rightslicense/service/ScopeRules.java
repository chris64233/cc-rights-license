package com.chris64233.cc.rightslicense.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 授权范围（日期闭区间、地域集合、媒介集合）的包含与交集判定。
 */
final class ScopeRules {

    private ScopeRules() {
    }

    record Scope(LocalDate startDate, LocalDate endDate,
                 List<String> territories, List<String> media) {
    }

    /** inner 是否完全落在 outer 内（闭区间）。 */
    static boolean contains(Scope outer, Scope inner) {
        return !inner.startDate().isBefore(outer.startDate())
                && !inner.endDate().isAfter(outer.endDate())
                && outer.territories().containsAll(inner.territories())
                && outer.media().containsAll(inner.media());
    }

    static List<String> notContained(List<String> inner, List<String> outer) {
        Set<String> outerSet = new LinkedHashSet<>(outer);
        List<String> missing = new ArrayList<>();
        for (String value : new LinkedHashSet<>(inner)) {
            if (!outerSet.contains(value)) {
                missing.add(value);
            }
        }
        return missing;
    }

    /** 日期闭区间是否重叠（首尾相接也算重叠，与既有冲突判定一致）。 */
    static boolean datesOverlap(LocalDate start1, LocalDate end1,
                                LocalDate start2, LocalDate end2) {
        return !start1.isAfter(end2) && !end1.isBefore(start2);
    }

    /** 地域 × 媒介组合是否存在交集。 */
    static boolean scopeCellsOverlap(Scope left, Scope right) {
        return datesOverlap(left.startDate(), left.endDate(), right.startDate(), right.endDate())
                && intersects(left.territories(), right.territories())
                && intersects(left.media(), right.media());
    }

    private static boolean intersects(List<String> left, List<String> right) {
        Set<String> rightSet = new LinkedHashSet<>(right);
        for (String value : left) {
            if (rightSet.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
