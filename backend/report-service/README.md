# Report Service

A Spring Boot service that is part of the **CBA Clean** project.

## Responsibility

The Report Service will be responsible for generating and providing reports
for the CBA Clean platform (e.g. cleaning job reports, summaries, exports).
No business functionality is implemented yet — this is the initial skeleton.

## Technologies

* Java 21
* Spring Boot 3.x
* Maven
* Spring Web
* Spring Boot Actuator
* JUnit 5

## Running the application

```bash
./mvnw spring-boot:run
```

The application starts on port `8080`. Health check endpoint:

```text
GET http://localhost:8080/actuator/health
```

## Running the tests

```bash
./mvnw test
```
