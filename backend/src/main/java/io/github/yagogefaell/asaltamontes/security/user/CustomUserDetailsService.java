package io.github.yagogefaell.asaltamontes.security.user;

import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.github.yagogefaell.asaltamontes.user.account.UserAccount;
import io.github.yagogefaell.asaltamontes.user.account.UserAccountRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userRepository;

    public CustomUserDetailsService(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Optional<UserAccount> userOptional = userRepository.findByUsernameIgnoreCase(username);

        if (userOptional.isEmpty()) {
            throw new UsernameNotFoundException("Usuario no encontrado con username: " + username);
        }

        UserAccount user = userOptional.get();

        return new CustomUserDetails(user); 
    }

}
