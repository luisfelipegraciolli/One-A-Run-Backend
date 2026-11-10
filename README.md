# One A Run Backend

One A Run Backend is the Kotlin/Spring Boot service used by the [One A Run Android app](https://github.com/luisfelipegraciolli/One-A-Run). It creates and joins head-to-head race sessions over HTTP, then receives GPS locations and publishes live race progress and results over MQTT.

## Features

- Create a race session with a target distance and a six-character join code.
- Let a second runner join using the code.
- Track both runners' distances from GPS coordinates and publish progress and race results over MQTT.
- Filter inaccurate, out-of-order, and implausible GPS updates.
- Run locally with an in-memory H2 database; no database server is needed for development.

## Requirements

- JDK 21
- Internet access to the configured MQTT broker

Gradle is provided by the wrapper, so a separate Gradle installation is not required.

## Get started

Clone the repository, then start the application:

```bash
git clone https://github.com/luisfelipegraciolli/One-a-Run-Backend.git
cd One-a-Run-Backend
./gradlew bootRun
```

The HTTP API is available at `http://localhost:8080`. The default configuration uses the public MQTT broker `broker.emqx.io:1883` and an in-memory H2 database. Race sessions are not persisted across application restarts.

Run the test suite with:

```bash
./gradlew test
```

## HTTP API

All session endpoints are under `/api/v1/race`.

### Create a session

`POST /api/v1/race` — provide a host name and a target distance from 100 to 1,000 meters.

```bash
curl -X POST http://localhost:8080/api/v1/race \
  -H 'Content-Type: application/json' \
  -d '{"hostRunnerName":"Alex","distanceInMeters":400}'
```

The response includes the session ID, join code, and host runner ID. Share the join code with the other runner.

### Join a session

`POST /api/v1/race/join`:

```bash
curl -X POST http://localhost:8080/api/v1/race/join \
  -H 'Content-Type: application/json' \
  -d '{"guestRunnerName":"Sam","joinCode":"ABC234"}'
```

The response includes the session ID and guest runner ID. The runner IDs are used in MQTT topics.

### Get a session

`GET /api/v1/race/{sessionId}` returns the session details.

## MQTT race updates

The backend subscribes to GPS location updates on `race/+/runner/+/location`. Publish a JSON location payload to `race/{sessionId}/runner/{runnerId}/location`, using the session and runner IDs returned by the HTTP API:

```json
{
  "latitude": -23.5505,
  "longitude": -46.6333,
  "timestamp": 1730390000000,
  "accuracy": 5.0
}
```

`timestamp` is optional and, when provided, is an epoch timestamp in milliseconds. `accuracy` is optional and measured in meters. The backend publishes each runner's progress to `race/{sessionId}/runner/{runnerId}/progress` and the race result to `race/{sessionId}/result`.

## Configuration

Default application settings are in [`src/main/resources/application.properties`](src/main/resources/application.properties). In particular, configure the MQTT connection with:

- `mqtt.broker-url`
- `mqtt.mqtt-broker-port`
- `mqtt.client-id-prefix`
- `mqtt.topic` for the default/test topic

Override Spring properties with environment variables or command-line arguments when deploying. The default H2 database is in memory and is intended for local development; configure a persistent database before using the service in an environment where session data must survive restarts.

## Help and documentation

- [Report a bug or request a feature](https://github.com/luisfelipegraciolli/One-a-Run-Backend/issues).
- See the [Android app repository](https://github.com/luisfelipegraciolli/One-A-Run) for the client that uses this backend.
- Review [`build.gradle.kts`](build.gradle.kts) for framework and dependency versions, and [`src/main/resources/application.properties`](src/main/resources/application.properties) for local defaults.

## Maintainers and contributing

One A Run is maintained by [Luis Graciolli](https://github.com/luisfelipegraciolli). Contributions are welcome. For substantial changes, open an issue to discuss the approach, then submit a pull request with a clear description and relevant tests. Run `./gradlew test` before submitting.

This project does not currently include a `LICENSE` file; check with the maintainers before reusing or distributing its code.
