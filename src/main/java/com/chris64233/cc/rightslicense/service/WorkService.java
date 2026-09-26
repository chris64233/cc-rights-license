package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.repo.RightsHolderRepository;
import com.chris64233.cc.rightslicense.repo.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WorkService {

    static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final WorkRepository workRepository;
    private final RightsHolderRepository holderRepository;

    public WorkService(WorkRepository workRepository, RightsHolderRepository holderRepository) {
        this.workRepository = workRepository;
        this.holderRepository = holderRepository;
    }

    @Transactional
    public Work createWork(String code, String title) {
        if (code == null || code.isBlank()) {
            throw BusinessException.badRequest("作品编号不能为空");
        }
        if (title == null || title.isBlank()) {
            throw BusinessException.badRequest("作品名称不能为空");
        }
        if (workRepository.existsByCode(code)) {
            throw BusinessException.conflict("作品编号已存在: " + code);
        }
        return workRepository.save(new Work(code, title));
    }

    @Transactional
    public RightsHolder addRightsHolder(String workCode, String name, BigDecimal sharePercent) {
        Work work = workRepository.findByCode(workCode)
                .orElseThrow(() -> BusinessException.notFound("作品不存在: " + workCode));
        validateShare(sharePercent);
        if (holderRepository.existsByWorkIdAndName(work.getId(), name)) {
            throw BusinessException.conflict("权利人已存在: " + name);
        }
        BigDecimal current = holderRepository.sumSharesByWorkId(work.getId());
        if (current.add(sharePercent).compareTo(HUNDRED) > 0) {
            throw BusinessException.unprocessable(
                    "份额之和不能超过 100%，当前累计 " + current.stripTrailingZeros().toPlainString()
                            + "%，无法再加入 " + sharePercent.stripTrailingZeros().toPlainString() + "%");
        }
        return holderRepository.save(new RightsHolder(work, name, sharePercent));
    }

    @Transactional(readOnly = true)
    public List<RightsHolder> listHolders(String workCode) {
        Work work = workRepository.findByCode(workCode)
                .orElseThrow(() -> BusinessException.notFound("作品不存在: " + workCode));
        return holderRepository.findByWorkIdOrderById(work.getId());
    }

    private void validateShare(BigDecimal sharePercent) {
        if (sharePercent == null) {
            throw BusinessException.badRequest("份额不能为空");
        }
        if (sharePercent.compareTo(BigDecimal.ZERO) <= 0 || sharePercent.compareTo(HUNDRED) > 0) {
            throw BusinessException.badRequest("份额必须在 (0, 100] 区间内");
        }
        if (sharePercent.stripTrailingZeros().scale() > 2) {
            throw BusinessException.badRequest("份额最多保留两位小数");
        }
    }
}
