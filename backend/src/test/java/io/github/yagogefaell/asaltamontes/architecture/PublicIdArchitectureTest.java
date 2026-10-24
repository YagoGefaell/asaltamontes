package io.github.yagogefaell.asaltamontes.architecture;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;

import io.github.yagogefaell.asaltamontes.AsaltamontesApplication;

/**
 * D-021 / T30a: the {@code bigint} primary keys never leave the backend. What the API shows (DTOs, which also are the
 * STOMP payloads) and accepts (path variables and query parameters) identifies resources by {@code public_id}
 * (UUID v7), by username or by slug. This fails the build when a response/payload DTO has a {@code Long} field called
 * {@code id} or {@code *Id}, or a controller takes a {@code Long} path variable or request parameter.
 *
 * <p>If one day an exception is really justified (it should not be: it would let people enumerate a resource), add its
 * full name here with a comment saying why, and mention it in the PR.
 */
@AnalyzeClasses(packagesOf = AsaltamontesApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class PublicIdArchitectureTest {

    /** "package.Class.member" of the allowed exceptions. */
    private static final Set<String> ALLOWED = Set.of();

    private static final DescribedPredicate<JavaClass> IN_DTO_PACKAGES =
            DescribedPredicate.describe("reside in a dto package", c -> c.getPackageName().contains(".dto"));

    @ArchTest
    static final ArchRule dtosDoNotExposeALongId = fields()
            .that().areDeclaredInClassesThat(IN_DTO_PACKAGES)
            .should(notBeALongId())
            .as("DTOs and WebSocket payloads must not have a Long field called id or *Id (use the public_id UUID)");

    @ArchTest
    static final ArchRule controllersDoNotTakeLongIds = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .should(notTakeALongPathVariableOrRequestParam())
            .as("Controllers must not take a Long path variable or request parameter (use the public_id UUID)");

    private static ArchCondition<JavaField> notBeALongId() {
        return new ArchCondition<>("not be a Long field called id or *Id") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                String name = field.getName();
                boolean isLong = field.getRawType().isEquivalentTo(Long.class) || field.getRawType().isEquivalentTo(long.class);
                boolean looksLikeId = name.equals("id") || name.endsWith("Id");
                if (isLong && looksLikeId && !ALLOWED.contains(field.getOwner().getName() + "." + name)) {
                    events.add(violated(field, field.getFullName() + " is a Long id exposed by a DTO"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> notTakeALongPathVariableOrRequestParam() {
        return new ArchCondition<>("not take a Long path variable or request parameter") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaParameter parameter : method.getParameters()) {
                    boolean isLong = parameter.getRawType().isEquivalentTo(Long.class)
                            || parameter.getRawType().isEquivalentTo(long.class);
                    boolean fromRequest = parameter.isAnnotatedWith("org.springframework.web.bind.annotation.PathVariable")
                            || parameter.isAnnotatedWith("org.springframework.web.bind.annotation.RequestParam");
                    if (isLong && fromRequest && !ALLOWED.contains(method.getFullName())) {
                        events.add(violated(method, method.getFullName() + " takes a Long id from the request"));
                    }
                }
            }
        };
    }
}
