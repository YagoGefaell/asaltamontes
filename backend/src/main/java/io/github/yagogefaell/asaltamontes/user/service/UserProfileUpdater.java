package io.github.yagogefaell.asaltamontes.user.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.domain.UserProfile;
import io.github.yagogefaell.asaltamontes.user.dto.UserUpdateDto;

@Component
public class UserProfileUpdater {

    public void applyChanges(UserAccount user, UserProfile profile, UserUpdateDto dto) {

        // USERNAME
        Optional.ofNullable(dto.username())
                .map(String::trim)
                .ifPresent(user::setUsername);

        // FULL NAME
        Optional.ofNullable(dto.fullName())
                .map(String::trim)
                .ifPresent(profile::setFullName);

        // BIO
        Optional.ofNullable(dto.bio())
                .map(String::trim)
                .ifPresent(profile::setBio);

        // CITY
        Optional.ofNullable(dto.city())
                .map(String::trim)
                .ifPresent(profile::setCity);

        // The profile picture is linked in UserService (media ownership checks, cleanup of the old one)

        // PUBLIC PROFILE
        Optional.ofNullable(dto.isPublicProfile())
                .ifPresent(profile::setIsPublicProfile);

        // WHO CAN START A PRIVATE CHAT
        Optional.ofNullable(dto.dmPolicy())
                .ifPresent(profile::setDmPolicy);

        // EMAIL (si decides permitirlo)
        Optional.ofNullable(dto.email())
                .map(String::trim)
                .ifPresent(user::setEmail);

        // RUNNING LEVEL (si lo añades al DTO en el futuro)
        //Optional.ofNullable(dto.runningLevel())
        //        .ifPresent(profile::setRunningLevel);

        // BIRTHDATE
        //Optional.ofNullable(dto.birthDate())
        //        .ifPresent(profile::setBirthDate);

        // PHONE NUMBER
        //Optional.ofNullable(dto.phoneNumber())
        //        .map(String::trim)
        //        .ifPresent(profile::setPhoneNumber);
    }
}
