package io.github.yagogefaell.asaltamontes.community.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.UserNotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.SortWhitelist;
import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;
import lombok.RequiredArgsConstructor;

/** Communities shown on another user's profile (T31b, D-023). */
@Service
@RequiredArgsConstructor
public class UserCommunitiesService {

    private static final int MAX_PAGE = 50;
    private static final SortWhitelist SORT = new SortWhitelist(
            Map.of("name", "c.name", "memberCount", "c.memberCount", "createdAt", "c.createdAt"), "name");

    private final CommunityMemberRepository memberRepository;
    private final UserAccountRepository userAccountRepository;
    private final BlockService blockService;

    /**
     * Only the PUBLIC communities she belongs to (not the ones she was banned from). Unknown or blocked in either
     * direction: the same 404 (D-022).
     */
    @Transactional(readOnly = true)
    public Page<CommunitySearchDto> publicCommunitiesOf(String username, UserAccount viewer, Pageable pageable) {
        Long targetId = userAccountRepository.findByUsernameIgnoreCase(username)
                .filter(u -> !blockService.isBlockedEitherWay(viewer.getId(), u.getId()))
                .orElseThrow(() -> new UserNotFoundException(username)).getId();
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE),
                pageable.getSort());
        return memberRepository.findPublicCommunitiesOf(targetId, viewer.getId(), SORT.apply(page));
    }
}
