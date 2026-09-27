package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.repo.LicenseGrantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 权利树查询：按授权号加载节点、直接下级、整棵子树、有效范围、版本历史。
 */
@Service
public class RightsTreeQueryService {

    private final LicenseGrantRepository grantRepository;

    public RightsTreeQueryService(LicenseGrantRepository grantRepository) {
        this.grantRepository = grantRepository;
    }

    @Transactional(readOnly = true)
    public LicenseGrant getGrant(String grantNo) {
        return grantRepository.findDetailByGrantNo(grantNo)
                .orElseThrow(() -> BusinessException.notFound("授权不存在: " + grantNo));
    }

    @Transactional(readOnly = true)
    public LicenseGrant getGrantById(Long id) {
        return grantRepository.findDetailById(id)
                .orElseThrow(() -> BusinessException.notFound("授权不存在: " + id));
    }

    /** 一棵子树的全部节点（含根自身），按层级（物化路径长度）与 id 排序；节点均已初始化全部关联 */
    @Transactional(readOnly = true)
    public List<LicenseGrant> getSubtree(String grantNo) {
        LicenseGrant root = getGrant(grantNo);
        List<Long> ids = grantRepository.findSubtree(root.descendantPrefix()).stream()
                .map(LicenseGrant::getId).toList();
        List<LicenseGrant> nodes = new java.util.ArrayList<>();
        nodes.add(root);
        ids.forEach(id -> nodes.add(getGrantById(id)));
        return nodes;
    }

    /** 直接下级 */
    @Transactional(readOnly = true)
    public List<LicenseGrant> getChildren(String grantNo) {
        LicenseGrant root = getGrant(grantNo);
        return grantRepository.findByParentIdOrderById(root.getId()).stream()
                .map(g -> getGrantById(g.getId()))
                .toList();
    }

    /** 某授权当前处于某状态的全部下级（含多级） */
    @Transactional(readOnly = true)
    public List<LicenseGrant> getDescendantsByStatus(String grantNo, GrantStatus status) {
        LicenseGrant root = getGrant(grantNo);
        return grantRepository.findSubtreeByStatus(root.descendantPrefix(), status).stream()
                .map(g -> getGrantById(g.getId()))
                .toList();
    }

    /** 层级链（根在前，自身在末） */
    @Transactional(readOnly = true)
    public List<LicenseGrant> getChain(String grantNo) {
        LicenseGrant grant = getGrant(grantNo);
        return grant.getChain().stream().map(this::getGrantById).toList();
    }
}
