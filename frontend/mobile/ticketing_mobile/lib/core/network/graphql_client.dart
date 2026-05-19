import 'package:ferry/ferry.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:gql_http_link/gql_http_link.dart';
import 'package:hive_flutter/hive_flutter.dart';

import '../auth/auth_service.dart';
import '../auth/secure_storage.dart';
import '../config/env.dart';

/// Initialize Hive for Ferry cache
Future<void> initHiveForFerry() async {
  await Hive.initFlutter();
}

/// Create Ferry client with caching and auth
Future<Client> createGraphQLClient(Ref ref) async {
  // Get auth token if available
  String? authToken;
  final authState = ref.read(authServiceProvider);
  if (authState.isAuthenticated) {
    authToken = await SecureTokenStorage.getAccessToken();
  }

  // HTTP link to GraphQL endpoint with auth header
  final httpLink = HttpLink(
    AppConfig.graphqlEndpoint,
    defaultHeaders: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      if (authToken != null) 'Authorization': 'Bearer $authToken',
    },
  );

  // Create cache
  final cache = Cache(
    possibleTypes: {},
  );

  return Client(
    link: httpLink,
    cache: cache,
    defaultFetchPolicies: {
      OperationType.query: FetchPolicy.CacheAndNetwork,
      OperationType.mutation: FetchPolicy.NetworkOnly,
    },
    addTypename: true,
  );
}

/// Provider for GraphQL client
final graphqlClientProvider = FutureProvider<Client>((ref) async {
  return createGraphQLClient(ref);
});

/// Simple provider that creates client synchronously (for when we know auth state)
final simpleGraphqlClientProvider = Provider<Client>((ref) {
  final authState = ref.watch(authServiceProvider);
  final authToken = authState.accessToken;

  final httpLink = HttpLink(
    AppConfig.graphqlEndpoint,
    defaultHeaders: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      if (authToken != null) 'Authorization': 'Bearer $authToken',
    },
  );

  final cache = Cache(possibleTypes: {});

  return Client(
    link: httpLink,
    cache: cache,
    defaultFetchPolicies: {
      OperationType.query: FetchPolicy.CacheAndNetwork,
      OperationType.mutation: FetchPolicy.NetworkOnly,
    },
    addTypename: true,
  );
});

/// Extension methods for common GraphQL operations
extension GraphQLClientExtensions on Client {
  /// Execute query and return data or throw
  Future<TData> queryOrThrow<TData, TVars>(
    OperationRequest<TData, TVars> request,
  ) async {
    final response = await this.request(request).first;

    if (response.hasErrors) {
      throw GraphQLException(
        response.graphqlErrors?.map((e) => e.message).toList() ?? [],
      );
    }

    if (response.data == null) {
      throw GraphQLException(['No data returned']);
    }

    return response.data as TData;
  }
}

/// GraphQL exception with error messages
class GraphQLException implements Exception {
  final List<String> messages;

  GraphQLException(this.messages);

  @override
  String toString() {
    if (messages.isEmpty) return 'Unknown GraphQL error';
    return messages.join(', ');
  }

  String get message => toString();
}
