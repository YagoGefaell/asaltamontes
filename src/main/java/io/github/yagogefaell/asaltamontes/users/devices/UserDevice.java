package io.github.yagogefaell.asaltamontes.users.devices;

import io.github.yagogefaell.asaltamontes.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name="userdevices")
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String fcmToken;

    private String tokenType;
    private boolean revoke;
    private boolean expired;
    private LocalDateTime lastSeen = LocalDateTime.now();
}
