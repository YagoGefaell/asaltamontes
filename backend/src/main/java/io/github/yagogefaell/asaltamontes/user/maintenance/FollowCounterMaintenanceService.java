package io.github.yagogefaell.asaltamontes.user.maintenance;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;

@Service
public class FollowCounterMaintenanceService {

    private static final int BATCH_SIZE = 1000;

    private final UserProfileRepository userProfileRepository;

    public FollowCounterMaintenanceService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void recountCountersInBatches() {

        int page = 0;
        Page<Long> ids;

        do {
            ids = userProfileRepository.findUserIds(PageRequest.of(page++, BATCH_SIZE));

            if (!ids.isEmpty()) {
                recountBatch(ids.getContent());
            }

        } while (!ids.isEmpty());
    }

    @Transactional
    protected void recountBatch(List<Long> ids) {
        userProfileRepository.recountFollowersByIds(ids);
        userProfileRepository.recountFollowingByIds(ids);
    }
}