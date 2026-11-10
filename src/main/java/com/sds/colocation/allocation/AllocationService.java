package com.sds.colocation.allocation;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sds.colocation.common.BusinessException;

@Service
@Transactional(readOnly = true)
public class AllocationService {

    private final AllocationRepository allocationRepository;
    private final ServerRepository serverRepository;
    private final AllocationViewMapper mapper;
    private final Clock clock;

    public AllocationService(AllocationRepository allocationRepository, ServerRepository serverRepository,
                           AllocationViewMapper mapper, Clock clock) {
        this.allocationRepository = allocationRepository;
        this.serverRepository = serverRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<AllocationView> findAll(Long customerId, boolean includeTerminated) {
        List<Allocation> allocations = includeTerminated
                ? allocationRepository.findAllWithDetails()
                : allocationRepository.findAllActiveWithDetails();
        return allocations.stream()
                .filter(allocation -> customerId == null || allocation.isOwnedBy(customerId))
                .sorted(Comparator.comparing(Allocation::getId))
                .map(mapper::toView)
                .toList();
    }

    public AllocationView findById(Long id) {
        return mapper.toView(getAllocation(id));
    }

    @Transactional
    public AllocationView update(Long id, AllocationUpdateRequest request) {
        if (request.id() != null && !request.id().equals(id)) {
            throw BusinessException.badRequest("ID_MISMATCH",
                    "URL의 임대 공간 ID(" + id + ")와 요청 본문의 ID(" + request.id() + ")가 다릅니다.");
        }
        Allocation allocation = getAllocation(id);
        allocation.updateMemo(request.memo());
        return mapper.toView(allocation);
    }

    @Transactional
    public AllocationView terminate(Long id) {
        Allocation allocation = getAllocation(id);
        if (!allocation.isActive()) {
            throw BusinessException.conflict("ALREADY_TERMINATED", "이미 해지된 임대 공간입니다.");
        }
        allocation.terminate(LocalDate.now(clock));
        allocation.getRack().removeUsedPower(allocation.getTotalPowerWatts());
        return mapper.toView(allocation);
    }

    @Transactional
    public ServerView completeReboot(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> BusinessException.notFound("서버", serverId));
        server.completeReboot();
        return ServerView.from(server);
    }

    private Allocation getAllocation(Long id) {
        return allocationRepository.findWithDetailsById(id)
                .orElseThrow(() -> BusinessException.notFound("임대 공간", id));
    }
}
