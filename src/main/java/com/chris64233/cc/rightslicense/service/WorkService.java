package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.domain.WorkRightHolder;
import com.chris64233.cc.rightslicense.repository.WorkRepository;
import com.chris64233.cc.rightslicense.web.dto.CreateWorkRequest;
import com.chris64233.cc.rightslicense.web.dto.RightHolderInput;
import com.chris64233.cc.rightslicense.web.dto.WorkResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WorkService {

    static final BigDecimal HUNDRED = new BigDecimal("100");

    private final WorkRepository workRepository;

    public WorkService(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    @Transactional
    public WorkResponse createWork(CreateWorkRequest request) {
        Set<String> names = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (RightHolderInput holder : request.rightHolders()) {
            if (!names.add(holder.holderName())) {
                throw BusinessException.badRequest("权利人重复: " + holder.holderName());
            }
            total = total.add(holder.sharePercent());
        }
        if (total.compareTo(HUNDRED) != 0) {
            throw BusinessException.badRequest("权利人份额之和必须精确等于 100%，当前为 " + total.stripTrailingZeros().toPlainString() + "%");
        }
        Work work = new Work(request.workCode(), request.title());
        for (RightHolderInput holder : request.rightHolders()) {
            work.addRightHolder(new WorkRightHolder(holder.holderName(), holder.sharePercent()));
        }
        try {
            work = workRepository.saveAndFlush(work);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("作品编号已存在: " + request.workCode());
        }
        return toResponse(work);
    }

    @Transactional(readOnly = true)
    public WorkResponse getWork(String workCode) {
        return toResponse(findWork(workCode));
    }

    @Transactional(readOnly = true)
    public Work findWork(String workCode) {
        return workRepository.findByWorkCode(workCode)
                .orElseThrow(() -> BusinessException.notFound("作品不存在: " + workCode));
    }

    private WorkResponse toResponse(Work work) {
        List<RightHolderInput> holders = work.getRightHolders().stream()
                .map(h -> new RightHolderInput(h.getHolderName(), h.getSharePercent()))
                .toList();
        BigDecimal total = work.getRightHolders().stream()
                .map(WorkRightHolder::getSharePercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WorkResponse(work.getId(), work.getWorkCode(), work.getTitle(), holders, total);
    }
}
