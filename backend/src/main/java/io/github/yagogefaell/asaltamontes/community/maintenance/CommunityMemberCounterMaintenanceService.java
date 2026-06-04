package io.github.yagogefaell.asaltamontes.community.maintenance;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.community.repository.CommunityRepository;

@Service
public class CommunityMemberCounterMaintenanceService {
    private static final int BATCH_SIZE = 1000;

    private final CommunityRepository communityRepository;

    public CommunityMemberCounterMaintenanceService(CommunityRepository communityRepository) {
        this.communityRepository = communityRepository;
    }

    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void recountCountersInBatches() {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        Slice<Long> slice;

        do {
            slice = communityRepository.findAllIds(pageable);
            if (!slice.isEmpty()) {
                recountBatch(slice.getContent());
            }
            pageable = slice.nextPageable();
        } while (slice.hasNext());
    }

    private void recountBatch(List<Long> ids) {
        communityRepository.recalculateMemberCountByIds(ids);
    }
}
