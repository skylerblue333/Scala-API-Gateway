package com.skycoin4444.gateway

import java.net.URI
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GatewayRoutesTest:
  @Test def longestPrefixWins(): Unit =
    val routes = GatewayRoutes.parse("/api=http://api.internal:8080,/api/admin=http://admin.internal:8081")
    assertEquals("/api/admin", GatewayRoutes.matchRoute(routes, "/api/admin/users").get.prefix)
    assertEquals("/api", GatewayRoutes.matchRoute(routes, "/api/users").get.prefix)

  @Test def preservesSuffixAndQuery(): Unit =
    val route = GatewayRoutes.parse("/api=https://service.example/base").head
    val target = GatewayRoutes.target(route, URI.create("http://gateway/api/users?id=42"))
    assertEquals("https://service.example/base/users?id=42", target.toString)

  @Test def rejectsUnsafeOrDuplicateRoutes(): Unit =
    assertThrows(classOf[IllegalArgumentException], () => GatewayRoutes.parse("api=http://localhost:1"))
    assertThrows(classOf[IllegalArgumentException], () => GatewayRoutes.parse("/api=file:///tmp/x"))
    assertThrows(classOf[IllegalArgumentException], () => GatewayRoutes.parse("/api=http://one,/api=http://two"))

  @Test def rootFallbackMatchesWhenNoSpecificRouteExists(): Unit =
    val routes = GatewayRoutes.parse("/=http://fallback:9000,/api=http://api:9001")
    assertEquals("/", GatewayRoutes.matchRoute(routes, "/other").get.prefix)
