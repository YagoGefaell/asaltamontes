package io.github.yagogefaell.asaltamontes.infrastructure.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.service.FollowService;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fictional demo data (T02b, D-014), created through the real services so passwords, slugs and counters
 * are exactly what the app would produce. Only with the "seed" profile, and only on a database without
 * users unless asaltamontes.seed.force=true. Deterministic: the same data every time. Logins: see README.
 */
@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    public static final String PASSWORD = "Montana-2026";
    public static final String DEMO_USERNAME = "demo";

    private static final String[][] PEOPLE = {
        {"demo", "Demo Asaltamontes"}, {"lucia.trail", "Lucía Fernández"}, {"marta_monte", "Marta Iglesias"},
        {"carmen.ultra", "Carmen Rodríguez"}, {"ines.cumbres", "Inés Varela"}, {"noa_corre", "Noa Castro"},
        {"sara.sendas", "Sara Domínguez"}, {"alba.montes", "Alba Pereira"}, {"irene_km42", "Irene López"},
        {"paula.desnivel", "Paula Otero"}, {"elena_vertical", "Elena Souto"}, {"julia.trotes", "Julia Martínez"},
        {"ana_pistas", "Ana Rey"}, {"laura.ultra", "Laura Gómez"}, {"rocio_sierra", "Rocío Blanco"},
        {"claudia.picos", "Claudia Vázquez"}, {"nerea_rutas", "Nerea Álvarez"}, {"sofia.trail", "Sofía Prieto"},
        {"maria.cimas", "María Seoane"}, {"andrea_bosque", "Andrea Lorenzo"}, {"olaia.montana", "Olaia Rial"},
        {"uxia_trail", "Uxía Barreiro"}, {"carla.km", "Carla Méndez"}, {"lidia_ruta", "Lidia Pazos"},
    };

    private static final String[][] COMMUNITIES = {
        {"Trail Vigo", "Salidas por las Cíes, el Galiñeiro y la Serra do Galiñeiro. Domingos a las 8."},
        {"Asaltamontes Coruña", "Rodaje suave entre semana y monte el fin de semana."},
        {"Ultra Galicia", "Para quien prepara su primer ultra o el décimo."},
        {"Iniciación al trail", "Del asfalto al monte sin prisas: técnica, material y rutas fáciles."},
        {"Montaña Asturias", "Picos de Europa, Ubiñas y todo lo que sube."},
        {"Corredoras de Madrid", "Sierra de Guadarrama y Pedriza. Quedadas cada sábado."},
    };

    private static final String[] MESSAGES = {
        "¿Vienes el domingo?", "¡Claro! ¿A qué hora salimos?", "A las 8 desde el parking", "Llevo frontal por si acaso",
        "¿Qué distancia hacemos?", "Unos 18 km con 900 de desnivel", "Perfecto, me apunto", "¿Alguien tiene bastones?",
        "Yo te dejo unos", "Mirad el parte, parece que llueve", "Con lluvia también se corre 😄", "Nos vemos allí",
        "¡Qué subida más dura!", "Mañana agujetas seguro", "¿Repetimos la semana que viene?",
    };

    private final UserAccountRepository userAccountRepository;
    private final UserService userService;
    private final FollowService followService;
    private final CommunityService communityService;
    private final MessageService messageService;

    @Value("${asaltamontes.seed.force:false}")
    private boolean force;

    @Override
    public void run(String... args) {
        if (userAccountRepository.existsByUsernameIgnoreCase(DEMO_USERNAME)) {
            log.info("Seed: the demo data is already there, nothing to do");
            return;
        }
        if (userAccountRepository.count() > 0 && !force) {
            log.warn("Seed: the database already has users; not seeding (set asaltamontes.seed.force=true to add the demo data anyway)");
            return;
        }

        Random random = new Random(2026);
        List<UserAccount> users = new ArrayList<>();
        for (String[] person : PEOPLE) {
            users.add(userService.registerUserWithProfile(
                    person[1], person[0], person[0].replace('.', '_') + "@ejemplo.test", PASSWORD, PASSWORD));
        }

        // Each one follows 3 to 7 others
        for (UserAccount user : users) {
            int follows = 3 + random.nextInt(5);
            for (int i = 0; i < follows; i++) {
                UserAccount other = users.get(random.nextInt(users.size()));
                if (!other.getId().equals(user.getId())) {
                    try {
                        followService.followUser(user.getId(), other.getUsername());
                    } catch (ConflictException alreadyFollowing) {
                        // Picked twice: fine
                    }
                }
            }
        }

        // Communities with 4 to 12 members each (demo owns the first one)
        for (int c = 0; c < COMMUNITIES.length; c++) {
            UserAccount owner = c == 0 ? users.get(0) : users.get(1 + random.nextInt(users.size() - 1));
            Community community = communityService.create(
                    new CreateCommunityDto(COMMUNITIES[c][0], COMMUNITIES[c][1], null, null), owner);
            // demo is also a member of two more, so the demo account has something to browse
            if (c == 1 || c == 3) {
                communityService.joinCommunity(community.getPublicId(), users.get(0));
            }
            int members = 4 + random.nextInt(9);
            for (int i = 0; i < members; i++) {
                UserAccount member = users.get(random.nextInt(users.size()));
                try {
                    communityService.joinCommunity(community.getPublicId(), member);
                } catch (ConflictException alreadyMember) {
                    // Owner or picked twice: fine
                }
            }
        }

        // demo chats with the first 8, plus a few chats between the others
        List<UserAccount[]> pairs = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            pairs.add(new UserAccount[] {users.get(0), users.get(i)});
        }
        for (int i = 0; i < 10; i++) {
            UserAccount a = users.get(1 + random.nextInt(users.size() - 1));
            UserAccount b = users.get(1 + random.nextInt(users.size() - 1));
            if (!a.getId().equals(b.getId())) {
                pairs.add(new UserAccount[] {a, b});
            }
        }
        for (UserAccount[] pair : pairs) {
            UUID conversationId = messageService
                    .createOrGetPrivateConversation(pair[0].getUsername(), pair[1].getUsername()).conversationId();
            sendConversation(conversationId, List.of(pair), random);
        }

        UUID group = messageService.createGroupConversation(users.get(0).getUsername(), "Quedada del domingo", null, null,
                users.subList(1, 7).stream().map(UserAccount::getUsername).toList()).conversationId();
        sendConversation(group, users.subList(0, 7), random);

        log.info("Seed: created {} users, {} communities and {} chats. Login: {} / {}",
                users.size(), COMMUNITIES.length, pairs.size() + 1, DEMO_USERNAME, PASSWORD);
    }

    private void sendConversation(UUID conversationId, List<UserAccount> participants, Random random) {
        int count = 3 + random.nextInt(12);
        for (int i = 0; i < count; i++) {
            UserAccount sender = participants.get(random.nextInt(participants.size()));
            messageService.processIncomingMessage(conversationId, sender.getId(), MESSAGES[(i + random.nextInt(3)) % MESSAGES.length]);
        }
    }
}
