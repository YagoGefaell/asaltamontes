package io.github.yagogefaell.asaltamontes.users.settings;

import io.github.yagogefaell.asaltamontes.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name="usersettings")
public class UserSettings {

    @Id
    private Long userId; // mismo que user.id

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private boolean darkMode = false;
    private boolean notificationsEnabled = true;
    private boolean lastSeenVisible = true;
}
