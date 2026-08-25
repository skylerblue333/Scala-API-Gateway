package com.skycoin4444.gateway

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import java.io.InputStream
import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.concurrent.Executors
import scala.jdk.CollectionConverters.*

object Main:
  private val HopByHop = Set("connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length")
  private val MaxRequestBytes = 1024 * 1024

  def main(args: Array[String]): Unit =
    val routes = GatewayRoutes.parse(sys.env.getOrElse("ROUTES", "/api=http://127.0.0.1:9000"))
    val port = parsePort(sys.env.getOrElse("PORT", "8080"))
    val timeout = Duration.ofSeconds(sys.env.get("UPSTREAM_TIMEOUT_SECONDS").map(_.toLong).filter(v => v >= 1 && v <= 30).getOrElse(5L))
    val client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build()
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor())
    server.createContext("/healthz", exchange => respond(exchange, 200, "{\"status\":\"ok\"}", "application/json"))
    server.createContext("/readyz", exchange => respond(exchange, 200, s"{\"ready\":true,\"routes\":${routes.size}}", "application/json"))
    server.createContext("/", exchange => proxy(exchange, routes, client, timeout))
    server.start()
    println(s"{\"event\":\"server_started\",\"service\":\"sky-scala-gateway\",\"port\":$port,\"routes\":${routes.size}}")

  private def proxy(exchange: HttpExchange, routes: Seq[GatewayRoute], client: HttpClient, timeout: Duration): Unit =
    try
      GatewayRoutes.matchRoute(routes, exchange.getRequestURI.getRawPath) match
        case None => respond(exchange, 404, "{\"error\":\"route_not_found\"}", "application/json")
        case Some(route) =>
          val body = readBounded(exchange.getRequestBody, MaxRequestBytes)
          val target = GatewayRoutes.target(route, exchange.getRequestURI)
          val builder = HttpRequest.newBuilder(target).timeout(timeout)
          exchange.getRequestHeaders.asScala.foreach { (name, values) =>
            if !HopByHop.contains(name.toLowerCase) then values.asScala.foreach(value => builder.header(name, value))
          }
          builder.header("X-Forwarded-Proto", "http")
          val publisher = if body.isEmpty then HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofByteArray(body)
          val request = builder.method(exchange.getRequestMethod, publisher).build()
          val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
          response.headers().map().asScala.foreach { (name, values) =>
            if !HopByHop.contains(name.toLowerCase) then values.asScala.foreach(value => exchange.getResponseHeaders.add(name, value))
          }
          exchange.getResponseHeaders.set("Cache-Control", "no-store")
          exchange.sendResponseHeaders(response.statusCode(), response.body().length)
          exchange.getResponseBody.write(response.body())
          exchange.close()
    catch
      case _: RequestTooLarge => respond(exchange, 413, "{\"error\":\"request_too_large\"}", "application/json")
      case _: Exception => respond(exchange, 502, "{\"error\":\"upstream_failure\"}", "application/json")

  private def readBounded(input: InputStream, max: Int): Array[Byte] =
    val output = new java.io.ByteArrayOutputStream()
    val buffer = Array.ofDim[Byte](8192)
    var total = 0
    var read = input.read(buffer)
    while read != -1 do
      total += read
      if total > max then throw RequestTooLarge()
      output.write(buffer, 0, read)
      read = input.read(buffer)
    output.toByteArray

  private def respond(exchange: HttpExchange, status: Int, body: String, contentType: String): Unit =
    val bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", contentType)
    exchange.getResponseHeaders.set("Cache-Control", "no-store")
    exchange.sendResponseHeaders(status, bytes.length)
    exchange.getResponseBody.write(bytes)
    exchange.close()

  private def parsePort(raw: String): Int =
    val port = raw.toInt
    require(port >= 1 && port <= 65535, "PORT must be 1-65535")
    port

  private final case class RequestTooLarge() extends RuntimeException
