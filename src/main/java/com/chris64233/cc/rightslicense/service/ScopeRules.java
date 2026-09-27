package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 授权范围（日期闭区间、地域集合、媒介集合）的包含与重叠判定。
 * 所有判定都是整份申请级别：调用方对全部 地域 × 媒介 × 日期 组合聚合后一次性给出结论。
 */
public final class ScopeRules {

    private ScopeRules() {
    }

    /** 闭区间重叠（首尾相接也算重叠，与现有授权冲突规则一致） */
    public static boolean datesOverlap(LocalDate startA, LocalDate endA,
                                       LocalDate startB, LocalDate endB) {
        return !startA.isAfter(endB) && !endA.isBefore(startB);
    }

    /** child 日期闭区间完全落在 parent 内 */
    public static boolean datesWithin(LocalDate childStart, LocalDate childEnd,
                                      LocalDate parentStart, LocalDate parentEnd) {
        return !childStart.isBefore(parentStart) && !childEnd.isAfter(parentEnd);
    }

    public static Set<String> intersect(List<String> left, List<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.retainAll(new LinkedHashSet<>(right));
        return result;
    }

    public static boolean containsAll(List<String> container, List<String> contained) {
        return new LinkedHashSet<>(container).containsAll(new LinkedHashSet<>(contained));
    }

    /**
     * 转授权申请的核心范围是否完全落在上级授权当前范围内（日期 + 全部地域 + 全部媒介）。
     */
    public static boolean containedInGrant(SublicenseApplication child, LicenseGrant parent) {
        return datesWithin(child.getStartDate(), child.getEndDate(),
                parent.getStartDate(), parent.getEndDate())
                && containsAll(parent.getTerritories(), child.getTerritories())
                && containsAll(parent.getMedia(), child.getMedia());
    }
}
