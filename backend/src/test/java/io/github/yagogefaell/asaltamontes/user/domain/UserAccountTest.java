package io.github.yagogefaell.asaltamontes.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class UserAccountTest {

    @Test
    void toStringAndHashCode_withProfile_doNotRecurse() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setUser(user);
        user.setProfile(profile);

        assertThatCode(() -> {
            user.toString();
            user.hashCode();
            profile.toString();
            profile.hashCode();
        }).doesNotThrowAnyException();
    }

    @Test
    void equality_isBasedOnTheId() {
        UserAccount first = new UserAccount();
        first.setId(1L);
        first.setUsername("ana");
        UserAccount sameId = new UserAccount();
        sameId.setId(1L);
        sameId.setUsername("changed");
        UserAccount notPersisted = new UserAccount();
        UserAccount otherNotPersisted = new UserAccount();

        assertThat(first).isEqualTo(sameId).hasSameHashCodeAs(sameId);
        assertThat(notPersisted).isNotEqualTo(otherNotPersisted);
        assertThat(notPersisted).isEqualTo(notPersisted);
    }
}
