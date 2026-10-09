package com.pml.shared.graphql;

import graphql.language.Argument;
import graphql.language.Definition;
import graphql.language.Directive;
import graphql.language.DirectivesContainer;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NamedNode;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.parser.Parser;
import graphql.parser.ParserOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The public contract, derived from the subgraphs' tags the way a GraphOS contract variant
 * derives it: every type, field and argument tagged {@code admin} or {@code internal} is removed,
 * and what remains must still be a schema.
 *
 * <p>Tags on a type apply to the type in every subgraph, so a type tagged in one subgraph is
 * excluded everywhere — the same union a contract applies. What the derivation reports is what
 * would make the published contract fail or leak:
 * <ul>
 *   <li>a public field or argument whose type was excluded — the contract would not compose;</li>
 *   <li>a public object type left with no fields — likewise;</li>
 *   <li>a public field whose name belongs to the admin vocabulary — the contract composes and
 *       shows every mobile client something only the console should see.</li>
 * </ul>
 */
final class PublicContract {

    static final Set<String> EXCLUDED_TAGS = Set.of("admin", "internal");

    /**
     * The platform's admin vocabulary, plus the verb prefixes that name platform-staff actions.
     * A public field matching one is a leak.
     */
    static final List<Pattern> ADMIN_VOCABULARY = List.of(
            Pattern.compile("^platformRevenue"),
            Pattern.compile("^stuckTransaction"),
            Pattern.compile("^systemHealth"),
            Pattern.compile("^suspend[A-Z]"),
            Pattern.compile("^resolve[A-Za-z]*Issue"),
            Pattern.compile("^resolveEscalation"),
            Pattern.compile("^forceComplete"),
            Pattern.compile("^reconcile[A-Z]"));

    /**
     * Names that match the vocabulary but are not platform-staff operations, argued one name at
     * a time rather than by loosening a pattern: widening {@code ^suspend[A-Z]} to spare one case
     * would also spare {@code suspendUser}. {@code suspendMember} is an organizer suspending
     * someone in their own organization, and organizer elements belong in the public contract.
     */
    static final Set<String> NOT_PLATFORM_STAFF = Set.of("suspendMember");

    private static final Set<String> BUILT_IN_SCALARS = Set.of("String", "Int", "Float", "Boolean", "ID");

    /** Type name → its public field names, across all subgraphs. */
    private final Map<String, Set<String>> publicFields = new TreeMap<>();
    private final Set<String> excludedTypes = new TreeSet<>();
    private final Set<String> knownTypes = new TreeSet<>();
    private final Set<String> objectTypes = new TreeSet<>();
    /** "Type.field" → the named type it returns, or "Type.field(arg)" → the argument's type. */
    private final Map<String, String> publicReferences = new LinkedHashMap<>();

    private PublicContract() {
    }

    /** @param subgraphs subgraph name → its SDL */
    static PublicContract derive(Map<String, String> subgraphs) {
        PublicContract contract = new PublicContract();
        List<Document> documents = new ArrayList<>();
        ParserOptions options = ParserOptions.newParserOptions().maxTokens(Integer.MAX_VALUE)
                .maxCharacters(Integer.MAX_VALUE).build();
        subgraphs.values().forEach(sdl -> documents.add(Parser.parse(
                graphql.parser.ParserEnvironment.newParserEnvironment().document(sdl).parserOptions(options).build())));

        for (Document document : documents) {
            for (Definition<?> definition : document.getDefinitions()) {
                if (definition instanceof NamedNode<?> named && isType(definition)) {
                    contract.knownTypes.add(named.getName());
                    if (definition instanceof ObjectTypeDefinition) {
                        contract.objectTypes.add(named.getName());
                    }
                    if (definition instanceof DirectivesContainer<?> directives && excluded(directives.getDirectives())) {
                        contract.excludedTypes.add(named.getName());
                    }
                }
            }
        }
        for (Document document : documents) {
            for (Definition<?> definition : document.getDefinitions()) {
                if (definition instanceof ObjectTypeDefinition object) {
                    contract.collect(object.getName(), object.getFieldDefinitions());
                } else if (definition instanceof InterfaceTypeDefinition anInterface) {
                    contract.collect(anInterface.getName(), anInterface.getFieldDefinitions());
                } else if (definition instanceof InputObjectTypeDefinition input) {
                    contract.collectInputs(input.getName(), input.getInputValueDefinitions());
                }
            }
        }
        return contract;
    }

    private static boolean isType(Definition<?> definition) {
        return definition instanceof ObjectTypeDefinition || definition instanceof InterfaceTypeDefinition
                || definition instanceof InputObjectTypeDefinition || definition instanceof EnumTypeDefinition
                || definition instanceof UnionTypeDefinition || definition instanceof ScalarTypeDefinition;
    }

    private void collect(String type, List<FieldDefinition> fields) {
        if (excludedTypes.contains(type)) {
            return;
        }
        Set<String> names = publicFields.computeIfAbsent(type, t -> new TreeSet<>());
        for (FieldDefinition field : fields) {
            if (excluded(field.getDirectives())) {
                continue;
            }
            names.add(field.getName());
            publicReferences.put(type + "." + field.getName(), namedType(field.getType()));
            for (InputValueDefinition argument : field.getInputValueDefinitions()) {
                if (!excluded(argument.getDirectives())) {
                    publicReferences.put(type + "." + field.getName() + "(" + argument.getName() + ")",
                            namedType(argument.getType()));
                }
            }
        }
    }

    private void collectInputs(String type, List<InputValueDefinition> fields) {
        if (excludedTypes.contains(type)) {
            return;
        }
        Set<String> names = publicFields.computeIfAbsent(type, t -> new TreeSet<>());
        for (InputValueDefinition field : fields) {
            if (!excluded(field.getDirectives())) {
                names.add(field.getName());
                publicReferences.put(type + "." + field.getName(), namedType(field.getType()));
            }
        }
    }

    static boolean excluded(List<Directive> directives) {
        for (Directive directive : directives) {
            if (!directive.getName().equals("tag")) {
                continue;
            }
            Argument name = directive.getArgument("name");
            if (name != null && name.getValue() instanceof StringValue value
                    && EXCLUDED_TAGS.contains(value.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static String namedType(Type<?> type) {
        if (type instanceof NonNullType nonNull) {
            return namedType(nonNull.getType());
        }
        if (type instanceof ListType list) {
            return namedType(list.getType());
        }
        return ((TypeName) type).getName();
    }

    // ── what the derivation answers ──────────────────────────────────────────

    /** Whether {@code Type.field} is in the public contract. */
    boolean exposes(String type, String field) {
        return publicFields.getOrDefault(type, Set.of()).contains(field);
    }

    Set<String> excludedTypes() {
        return excludedTypes;
    }

    /** Public fields and arguments whose type the contract removed: the contract would not compose. */
    List<String> dangling() {
        List<String> dangling = new ArrayList<>();
        publicReferences.forEach((where, type) -> {
            if (excludedTypes.contains(type)) {
                dangling.add(where + " → " + type);
            }
        });
        return dangling;
    }

    /** Public object types left with no field — also a contract that does not compose. */
    List<String> emptied() {
        List<String> emptied = new ArrayList<>();
        objectTypes.forEach(type -> {
            if (!excludedTypes.contains(type) && publicFields.getOrDefault(type, Set.of()).isEmpty()) {
                emptied.add(type);
            }
        });
        return emptied;
    }

    /** Public fields whose names belong to the admin vocabulary. */
    List<String> adminVocabulary() {
        List<String> leaks = new ArrayList<>();
        publicFields.forEach((type, fields) -> fields.forEach(field -> {
            if (!NOT_PLATFORM_STAFF.contains(field)
                    && ADMIN_VOCABULARY.stream().anyMatch(pattern -> pattern.matcher(field).find())) {
                leaks.add(type + "." + field);
            }
        }));
        return leaks;
    }

    /** Named types referenced by public fields that no subgraph defines — a derivation bug, not a leak. */
    Set<String> unknownReferences() {
        Set<String> unknown = new LinkedHashSet<>();
        publicReferences.values().forEach(type -> {
            if (!knownTypes.contains(type) && !BUILT_IN_SCALARS.contains(type)) {
                unknown.add(type);
            }
        });
        return unknown;
    }
}
