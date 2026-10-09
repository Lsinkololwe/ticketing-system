package com.pml.shared.security.publicop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.Parser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pure rules: is this request body a single query that stays inside a {@link PublicOperationPolicy}?
 * No I/O, no state, so every rule is unit-testable on its own.
 */
public final class PublicOperationRules {

    /** Why a body was refused, as a low-cardinality label safe to use as a metric tag. */
    public enum Verdict {
        ALLOWED, EMPTY, NOT_AN_OBJECT, NO_QUERY, PERSISTED, UNPARSEABLE, MULTIPLE_OPERATIONS, NOT_A_QUERY,
        ROOT_NOT_ALLOWED, INTROSPECTION, ROOT_FRAGMENT, ENTITIES_NOT_ALLOWED, TOO_DEEP, TOO_LARGE, BAD_FRAGMENT
    }

    private static final Set<String> REPRESENTATION_KEYS = Set.of("__typename", "id");
    private static final int MAX_REPRESENTATIONS = 100;

    private final PublicOperationPolicy policy;
    private final ObjectMapper json;

    public PublicOperationRules(PublicOperationPolicy policy, ObjectMapper json) {
        this.policy = policy;
        this.json = json;
        // The parser loads its message bundle on first use. Do that here, on the constructing thread, rather
        // than on a reactive one the first time a visitor posts a query.
        Parser.parse("{ warmUp }");
    }

    public Verdict judge(byte[] body) {
        if (body == null || body.length == 0) {
            return Verdict.EMPTY;
        }
        try {
            JsonNode root = json.readTree(body);
            if (root == null || !root.isObject()) {
                return Verdict.NOT_AN_OBJECT; // a batch array, or a scalar
            }
            JsonNode query = root.get("query");
            if (query == null || !query.isTextual()) {
                return Verdict.NO_QUERY;
            }
            if (root.has("extensions") && root.get("extensions").has("persistedQuery")) {
                return Verdict.PERSISTED;
            }
            Document document = Parser.parse(query.asText());
            List<OperationDefinition> operations = document.getDefinitionsOfType(OperationDefinition.class);
            Map<String, FragmentDefinition> fragments = new HashMap<>();
            for (FragmentDefinition fragment : document.getDefinitionsOfType(FragmentDefinition.class)) {
                fragments.put(fragment.getName(), fragment);
            }
            if (operations.size() != 1 || operations.size() + fragments.size() != document.getDefinitions().size()
                    || fragments.size() > policy.maxFragments()) {
                return operations.size() != 1 ? Verdict.MULTIPLE_OPERATIONS : Verdict.TOO_LARGE;
            }
            OperationDefinition operation = operations.get(0);
            if (operation.getOperation() != OperationDefinition.Operation.QUERY) {
                return Verdict.NOT_A_QUERY;
            }
            List<Selection> roots = operation.getSelectionSet().getSelections();
            if (roots.isEmpty()) {
                return Verdict.ROOT_NOT_ALLOWED;
            }
            boolean entities = false;
            for (Selection root0 : roots) {
                if (!(root0 instanceof Field field)) {
                    return Verdict.ROOT_FRAGMENT; // fragments live below a field, never at the root
                }
                String name = field.getName();
                if (name.startsWith("__")) {
                    return Verdict.INTROSPECTION;
                }
                if ("_entities".equals(name)) {
                    entities = true;
                } else if (!policy.rootFields().contains(name)) {
                    return Verdict.ROOT_NOT_ALLOWED;
                }
            }
            if (entities) {
                if (roots.size() != 1 || policy.entityFields().isEmpty()) {
                    return Verdict.ENTITIES_NOT_ALLOWED;
                }
                return entitiesAllowed((Field) roots.get(0), root.get("variables")) ? Verdict.ALLOWED
                        : Verdict.ENTITIES_NOT_ALLOWED;
            }
            return within(operation.getSelectionSet(), fragments);
        } catch (Exception notParseable) {
            return Verdict.UNPARSEABLE;
        }
    }

    /** Depth and size, counted through fragment spreads; a fragment that cannot be resolved or loops is refused. */
    private Verdict within(SelectionSet root, Map<String, FragmentDefinition> fragments) {
        int[] nodes = {0};
        int[] depthSeen = {0};
        Verdict[] failure = {null};
        walk(root, 1, fragments, new HashSet<>(), nodes, depthSeen, failure);
        return failure[0] != null ? failure[0] : Verdict.ALLOWED;
    }

    private void walk(SelectionSet set, int depth, Map<String, FragmentDefinition> fragments, Set<String> onPath,
                      int[] nodes, int[] depthSeen, Verdict[] failure) {
        if (set == null || failure[0] != null) {
            return;
        }
        if (depth > policy.maxDepth()) {
            failure[0] = Verdict.TOO_DEEP;
            return;
        }
        for (Selection selection : set.getSelections()) {
            if (failure[0] != null) {
                return;
            }
            if (selection instanceof Field field) {
                if (++nodes[0] > policy.maxNodes()) {
                    failure[0] = Verdict.TOO_LARGE;
                    return;
                }
                walk(field.getSelectionSet(), depth + 1, fragments, onPath, nodes, depthSeen, failure);
            } else if (selection instanceof InlineFragment inline) {
                walk(inline.getSelectionSet(), depth, fragments, onPath, nodes, depthSeen, failure);
            } else if (selection instanceof FragmentSpread spread) {
                FragmentDefinition definition = fragments.get(spread.getName());
                if (definition == null || !onPath.add(spread.getName())) {
                    failure[0] = Verdict.BAD_FRAGMENT; // unknown, or a cycle
                    return;
                }
                walk(definition.getSelectionSet(), depth, fragments, onPath, nodes, depthSeen, failure);
                onPath.remove(spread.getName());
            }
        }
    }

    /**
     * {@code _entities} only as the router issues it for a federated reference: inline fragments on an allowlisted
     * type selecting allowlisted leaf fields, over representations that carry nothing but a type and an id.
     */
    private boolean entitiesAllowed(Field entities, JsonNode variables) {
        if (entities.getSelectionSet() == null || entities.getSelectionSet().getSelections().isEmpty()) {
            return false;
        }
        for (Selection selection : entities.getSelectionSet().getSelections()) {
            if (!(selection instanceof InlineFragment inline) || inline.getTypeCondition() == null) {
                return false;
            }
            Set<String> allowed = policy.entityFields().get(inline.getTypeCondition().getName());
            if (allowed == null) {
                return false;
            }
            for (Selection leaf : inline.getSelectionSet().getSelections()) {
                if (!(leaf instanceof Field field) || field.getSelectionSet() != null
                        || !(allowed.contains(field.getName()) || "__typename".equals(field.getName()))) {
                    return false;
                }
            }
        }
        if (variables == null || !variables.isObject()) {
            return false;
        }
        JsonNode representations = variables.get("representations");
        if (representations == null || !representations.isArray() || representations.size() > MAX_REPRESENTATIONS) {
            return false;
        }
        for (JsonNode representation : representations) {
            if (!representation.isObject() || !representation.has("__typename")
                    || !policy.entityFields().containsKey(representation.get("__typename").asText())) {
                return false;
            }
            for (var names = representation.fieldNames(); names.hasNext(); ) {
                if (!REPRESENTATION_KEYS.contains(names.next())) {
                    return false;
                }
            }
        }
        return true;
    }
}
