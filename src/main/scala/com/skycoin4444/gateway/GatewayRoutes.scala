package com.skycoin4444.gateway

import java.net.URI

final case class GatewayRoute(prefix: String, upstream: URI)

object GatewayRoutes:
  def parse(raw: String): Vector[GatewayRoute] =
    require(raw != null && raw.nonEmpty, "ROUTES is required")
    val routes = raw.split(',').toVector.map(_.trim).filter(_.nonEmpty).map { entry =>
      val parts = entry.split("=", 2)
      require(parts.length == 2, "each route must be prefix=upstream")
      val prefix = normalizePrefix(parts(0).trim)
      val upstream = URI.create(parts(1).trim)
      require(Set("http", "https").contains(Option(upstream.getScheme).getOrElse("")), "upstream must use http or https")
      require(upstream.getHost != null && upstream.getRawUserInfo == null && upstream.getRawFragment == null, "invalid upstream URI")
      GatewayRoute(prefix, upstream)
    }
    require(routes.nonEmpty && routes.size <= 100, "1-100 routes are required")
    require(routes.map(_.prefix).distinct.size == routes.size, "duplicate route prefix")
    routes.sortBy(route => -route.prefix.length)

  def matchRoute(routes: Seq[GatewayRoute], path: String): Option[GatewayRoute] =
    val safePath = Option(path).filter(_.startsWith("/")).getOrElse("/")
    routes.find(route => safePath == route.prefix || safePath.startsWith(route.prefix + "/") || route.prefix == "/")

  def target(route: GatewayRoute, requestUri: URI): URI =
    val requestPath = Option(requestUri.getRawPath).getOrElse("/")
    val suffix = if route.prefix == "/" then requestPath else requestPath.stripPrefix(route.prefix) match
      case "" => "/"
      case value if value.startsWith("/") => value
      case value => "/" + value
    val basePath = Option(route.upstream.getRawPath).filter(_.nonEmpty).getOrElse("").stripSuffix("/")
    new URI(route.upstream.getScheme, null, route.upstream.getHost, route.upstream.getPort, basePath + suffix, requestUri.getRawQuery, null)

  private def normalizePrefix(value: String): String =
    require(value.startsWith("/") && value.length <= 128, "route prefix must start with / and be <=128 characters")
    if value == "/" then value else value.stripSuffix("/")
