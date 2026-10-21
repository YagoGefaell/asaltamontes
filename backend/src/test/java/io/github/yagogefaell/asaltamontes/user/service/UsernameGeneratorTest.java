package io.github.yagogefaell.asaltamontes.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;

class UsernameGeneratorTest {

    @Test
    void normalize_removesAccentsSpacesAndSymbols() {
        assertThat(UsernameGenerator.normalize("María José")).isEqualTo("mariajose");
        assertThat(UsernameGenerator.normalize("Zoë O'Brien-Núñez")).isEqualTo("zoeobriennunez");
        assertThat(UsernameGenerator.normalize(".ana.")).isEqualTo("ana");
    }

    @Test
    void normalize_fallsBackWhenNothingUsableIsLeft() {
        assertThat(UsernameGenerator.normalize(null)).isEqualTo(UsernameGenerator.FALLBACK);
        assertThat(UsernameGenerator.normalize("李娜")).isEqualTo(UsernameGenerator.FALLBACK);
        assertThat(UsernameGenerator.normalize("Al")).isEqualTo(UsernameGenerator.FALLBACK);
    }

    @Test
    void normalize_truncatesTo30Characters() {
        assertThat(UsernameGenerator.normalize("a".repeat(40))).hasSize(30);
    }

    @Test
    void generate_addsTheFirstFreeNumericSuffix() {
        Set<String> taken = Set.of("mariajose", "mariajose1");
        UserAccountRepository repository = mock(UserAccountRepository.class);
        when(repository.existsByUsernameIgnoreCase(anyString())).thenAnswer(call -> taken.contains(call.getArgument(0)));

        assertThat(new UsernameGenerator(repository).generate("María José")).isEqualTo("mariajose2");
    }

    @Test
    void generate_keepsTheResultWithin30CharactersWhenAddingASuffix() {
        String longBase = "b".repeat(30);
        UserAccountRepository repository = mock(UserAccountRepository.class);
        when(repository.existsByUsernameIgnoreCase(anyString())).thenAnswer(call -> call.getArgument(0).equals(longBase));

        String generated = new UsernameGenerator(repository).generate(longBase);

        assertThat(generated).hasSize(30).endsWith("1");
    }
}
