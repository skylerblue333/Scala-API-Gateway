# Sky Scala API Gateway

**Status: engineering beta.** This repository now contains a real Scala 3 reverse-proxy gateway boundary rather than the previous Python anomaly-demo placeholder.

## Implemented

- static `ROUTES` configuration using `prefix=http(s)://upstream`
- validated HTTP/HTTPS upstream URIs
- longest-prefix route matching
- path suffix and query-string preservation
- bounded request bodies (1 MiB)
- upstream connect/request timeout
- hop-by-hop header filtering
- redirects disabled on the upstream client
- `/healthz` and `/readyz`
- Java 21 virtual-thread request execution
- Scala/JUnit routing tests
- Maven build, dependency audit, non-root Docker image, and CI health smoke test

Example:

```bash
ROUTES='/api=http://api:8080,/identity=http://identity:8080' \
  java -jar target/sky-scala-gateway-0.1.0.jar
```

## Boundaries

This is not a complete production API-management platform. It does not provide authentication, authorization, distributed rate limiting, service discovery, retries/circuit breaking, TLS termination, WebSocket proxying, load balancing, tenant policy, persistent configuration, control-plane APIs, WAF behavior, distributed tracing propagation guarantees, or verified production deployment.

The intended SKYCOIN4444 integration role is a small independently deployable routing primitive or reference boundary. Production traffic should only use it after the missing security, resilience, observability, deployment, and operational controls are implemented and verified.

## Verification

```bash
mvn clean verify
docker build -t sky-scala-gateway .
```

CI is the merge gate; this README does not claim success until GitHub Actions verifies the exact branch head.

## License

See `LICENSE`.
