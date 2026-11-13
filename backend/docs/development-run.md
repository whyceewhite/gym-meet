# Local Development Environment
This document provides steps for setting up a development environment for developing and running this module locally.

### Table of Contents
* [Prerequisites](#prerequisites)
* [Installing](#installing)
* [Configuring](#configuring)
* [Building](#building)
* [Running](#running)
* [Importing results](#importing-results)

## Prerequisites

Refer to [Development Setup](development-setup.md) for prerequisite software, database and local environment setup information.

## Installing
```text
git clone git@github.com:doubletuck/gym-meet.git
cd gym-meet
```

## Configuring
The [src/main/resources/application.yml](../src/main/resources/application.yml) contains configuration values used by the Spring framework. The `application.yml` file is the default file from which all `application-{env}.yml` files will inherit.

| properties file       | environment                                                                               |
|-----------------------|-------------------------------------------------------------------------------------------|
| application.yml       | The default properties file. The other environment files can override values in this file. |
| application-local.yml | Used for local development on a workstation.                                              |

Files for deployed environments (e.g. `application-prod.yml`) will be added when there is somewhere to deploy to.

## Creating the database schema
Prior to running the application, you will need to have the database started. Review the [Development Setup](development-setup.md) guide if the database is not already set up.

The database schema will automatically be created using Flyway when the application is started as described in the [Running](#running) section. The [database/migration](../src/main/resources/db/migration) directory contains the scripts for establishing the schema.

### Optional: Clear database
If you want to start with a clean database, then running the following command will drop all schema objects in the database.
```shell
mvn flyway:clean -Dspring.profiles.active=local -Dflyway.url=jdbc:postgresql://localhost:5432/gymmeet -Dflyway.user=postgres -Dflyway.password=gympass -Dflyway.cleanDisabled=false
```

### Optional: Run migration

Recompile to ensure the latest SQL definitions are in the target/classes directory.
```shell
mvn clean compile
```

After clearing the database, run the migration script(s).
```shell
mvn flyway:migrate -Dspring.profiles.active=local -Dflyway.url=jdbc:postgresql://localhost:5432/gymmeet -Dflyway.user=postgres -Dflyway.password=gympass
```

NOTE: Whenever new migration scripts are created, run the above command to have them applied to the database.

## Building
```shell
mvn install 
```

## Testing
```shell
mvn verify
```
This will:
- run unit tests
- run integration tests
- validate the flyway migration scripts DDL matches the entity definitions

## Running
The `local` application properties profile, [application-local.yml](../src/main/resources/application-local.yml), defaults to local development environment values. If you followed the development setup guide, then the configuration will work as is. If you modified your setup (i.e., changed the database name or password), then you will need to adjust the properties values appropriately.

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

`spring-boot.run.profiles` is the `spring-boot-maven-plugin`'s own parameter for this; the `run` goal forks a separate JVM, so a plain `-Dspring.profiles.active=local` on the `mvn` command line only sets that property on the Maven process itself and never reaches the forked app.

or 

```shell
java -jar target/gym-meet.jar --spring.profiles.active=local
```

The app will be accessible via http://localhost:8080.

Spring Security's defaults are in effect until the app's security is configured: every endpoint requires HTTP basic authentication as the user `user`, with a password generated at startup and printed in the log (`Using generated security password: …`). To test that the app is up:
```shell
curl -u user:<generated password> http://localhost:8080/actuator
```

To stop the server, issue a Control-C command in the shell where the service is running.

## Importing results
Competition results are loaded from the files under `data/` by running the app with the `import` profile:
```shell
mvn spring-boot:run -Dspring-boot.run.profiles=local,import -Dspring-boot.run.arguments="--import.path=data/2026/2026-WORLD-CHAMPIONSHIPS"
```
See [import-process.md](import-process.md) for the arguments and what an import does.
