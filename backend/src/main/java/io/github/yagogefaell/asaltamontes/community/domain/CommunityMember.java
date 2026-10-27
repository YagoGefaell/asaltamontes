package io.github.yagogefaell.asaltamontes.community.domain;

import java.time.LocalDateTime;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "community_members",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_community_user", 
            columnNames = {"community_id", "user_account_id"}
        )
    }
)
public class CommunityMember {

    public CommunityMember(Community community, UserAccount userAccount, MemberStatus status) {
        this.community = community;
        this.userAccount = userAccount;
        this.status = status;
    }

    public CommunityMember(Community community, UserAccount userAccount, CommunityRole role) {
        this.community = community;
        this.userAccount = userAccount;
        this.role = role;
    }

    public CommunityMember(Community community, UserAccount userAccount) {
        this.community = community;
        this.userAccount = userAccount;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunityRole role = CommunityRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    /** Who expelled her and with which role at that moment (T19); empty unless BANNED. */
    @Column(name = "banned_by")
    private Long bannedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "banned_by_role", length = 20)
    private CommunityRole bannedByRole;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
    }
}