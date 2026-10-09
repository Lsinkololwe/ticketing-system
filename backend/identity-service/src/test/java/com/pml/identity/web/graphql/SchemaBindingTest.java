package com.pml.identity.web.graphql;

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every account and authentication operation the SDL declares has a resolver bound to it, and the
 * SDL still parses after the buyer-auth removal. DGS binds by method name, so a declared field with
 * no method answers with an error the first time anyone calls it.
 */
@Tag("L4")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · the SDL parses, and every user operation it declares is bound")
class SchemaBindingTest {

    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");
    private static final Path SOURCES = Path.of("src/main/java/com/pml/identity/web/graphql");

    /** The operations this change owns: the user and account surface. */
    private static final Set<String> USER_OPERATIONS = Set.of(
            "me", "user", "userByEmail", "userByPhone", "users", "usersByRole", "userStats",
            "logout", "updateMyProfile", "createUser", "updateUser", "suspendUser", "unsuspendUser",
            "lockUser", "unlockUser", "deactivateUser", "activateUser", "addUserRole", "removeUserRole",
            "setUserRoles", "syncUserFromKeycloak", "syncAllUsersFromKeycloak");

    @Test
    @DisplayName("the schema parses")
    void parses() throws IOException {
        TypeDefinitionRegistry registry = new SchemaParser().parse(Files.readString(SDL));
        assertThat(registry.getType("User")).isPresent();
        assertThat(registry.getType("Contact")).isPresent();
        assertThat(registry.getType("AccountState")).isPresent();
        assertThat(registry.getType("ContactType")).isPresent();
    }

    @Test
    @DisplayName("each user operation in the SDL has a resolver method of the same name, and none is declared twice")
    void userOperationsAreBound() throws IOException {
        TypeDefinitionRegistry registry = new SchemaParser().parse(Files.readString(SDL));
        List<String> declared = new ArrayList<>();
        for (String root : List.of("Query", "Mutation")) {
            registry.getType(root).ifPresent(type -> declared.addAll(fields(type)));
            registry.objectTypeExtensions().getOrDefault(root, List.of())
                    .forEach(extension -> declared.addAll(fields(extension)));
        }
        Set<String> bound = boundMethods();

        List<String> unbound = new ArrayList<>();
        for (String operation : USER_OPERATIONS) {
            assertThat(declared.stream().filter(operation::equals).count()).as(operation + " declared once").isEqualTo(1);
            if (!bound.contains(operation)) {
                unbound.add(operation);
            }
        }
        assertThat(unbound).isEmpty();
    }

    private static List<String> fields(Object type) {
        List<FieldDefinition> definitions = type instanceof ObjectTypeExtensionDefinition extension
                ? extension.getFieldDefinitions() : ((ObjectTypeDefinition) type).getFieldDefinitions();
        return definitions.stream().map(FieldDefinition::getName).toList();
    }

    private static Set<String> boundMethods() throws IOException {
        Pattern bound = Pattern.compile("@Dgs(?:Query|Mutation)\\b[^{;]*?\\bpublic\\s+[\\w<>, ?]+?\\s+(\\w+)\\s*\\(");
        Set<String> names = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            for (Path file : walk.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = bound.matcher(Files.readString(file));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
        return names;
    }
}
