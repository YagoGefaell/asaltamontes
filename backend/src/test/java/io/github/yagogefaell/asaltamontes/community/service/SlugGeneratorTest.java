package io.github.yagogefaell.asaltamontes.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    @Test
    void lowercasesAndJoinsWordsWithSingleHyphens() {
        assertThat(SlugGenerator.slugify("Trail Vigo / Sur #1")).isEqualTo("trail-vigo-sur-1");
        assertThat(SlugGenerator.slugify("  --Asaltamontes   Female--  ")).isEqualTo("asaltamontes-female");
    }

    @Test
    void removesAccentsAndKeepsTheLetter() {
        assertThat(SlugGenerator.slugify("Montaña Ñandú Àlex Çàmping")).isEqualTo("montana-nandu-alex-camping");
    }

    @Test
    void fallsBackWhenNothingIsLeft() {
        assertThat(SlugGenerator.slugify("🏔️ ¡¿?!")).isEqualTo("comunidad");
        assertThat(SlugGenerator.slugify(null)).isEqualTo("comunidad");
    }

    @Test
    void isCutAt100CharactersWithoutATrailingHyphen() {
        String slug = SlugGenerator.slugify("a".repeat(99) + " bbbb");
        assertThat(slug).hasSizeLessThanOrEqualTo(100).doesNotEndWith("-").isEqualTo("a".repeat(99));
    }

    @Test
    void normalizeName_trimsAndCollapsesSpaces() {
        assertThat(SlugGenerator.normalizeName("  Trail\t Vigo  ")).isEqualTo("Trail Vigo");
    }
}
