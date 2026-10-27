package io.github.yagogefaell.asaltamontes.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;

/** BUG-070: unfollowing must only touch the counters when a follow row really existed. */
class UserFollowCountersIT extends AbstractIntegrationTest {

    @Autowired
    private UserProfileRepository profiles;

    private void follow(UserAccount who, UserAccount target) throws Exception {
        mockMvc.perform(post("/api/users/follow/{u}", target.getUsername()).with(auth.bearer(who)))
                .andExpect(status().isOk());
    }

    private void unfollow(UserAccount who, UserAccount target) throws Exception {
        mockMvc.perform(delete("/api/users/follow/{u}", target.getUsername()).with(auth.bearer(who)))
                .andExpect(status().is2xxSuccessful());
    }

    private long following(UserAccount u) {
        return profiles.findByUserId(u.getId()).orElseThrow().getFollowingCount();
    }

    private long followers(UserAccount u) {
        return profiles.findByUserId(u.getId()).orElseThrow().getFollowersCount();
    }

    @Test
    void repeatedUnfollow_doesNotUndercountOtherFollows() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UserAccount dana = testData.createUser();
        follow(ana, bea);
        follow(ana, cris);
        follow(dana, bea);

        unfollow(ana, bea);
        unfollow(ana, bea);

        assertThat(following(ana)).isEqualTo(1);
        assertThat(followers(bea)).isEqualTo(1);
        assertThat(followers(cris)).isEqualTo(1);
    }

    @Test
    void unfollowingSomeoneNotFollowed_leavesCountersUntouched() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UserAccount dana = testData.createUser();
        follow(ana, cris);
        follow(dana, bea);

        unfollow(ana, bea);

        assertThat(following(ana)).isEqualTo(1);
        assertThat(followers(bea)).isEqualTo(1);
    }
}
