package pe.edu.nova.java.libs.outbox;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("pe.edu.nova.java.libs.outbox");

    @Test
    void theContractDoesNotDependOnItsStores() {
        noClasses()
                .that()
                .resideInAPackage("pe.edu.nova.java.libs.outbox")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..outbox.jdbc..", "..outbox.memory..", "java.sql..")
                .check(classes);
    }

    @Test
    void theCoreUsesNoFramework() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "io.quarkus..", "jakarta..", "com.fasterxml..")
                .check(classes);
    }
}
