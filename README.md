Getting Started
=========

#### What You Need

[Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
and [Maven 3](https://maven.apache.org/).

Some of the libraries referenced by this project are currently private to
IRT, so you will need to have Maven configured to use our internal repository
(see https://medwiki.stanford.edu/display/kbase/Maven).

#### Run Locally

Recommended steps for development:

- Clone the repository
- Drag the directory to IntelliJ
- Run Main.java in the debugger

The above steps should allow you to modify the HTML on the fly, and
even some of the server code (the IntelliJ debugger reloads code on
the fly for at least some cases).

To adjust your configuration, copy the sample properties file rather
than modifying it, so you won't accidentally commit your local changes.
The default configuration uses an embedded HSQLDB database. You will
need to adjust your local properties if you use a different database.

```
cp sample.properties local.properties
```

#### Maven Instructions

Build everything, create the database schema, and start the server.

```
mvn -DskipTests clean package
java -jar target/vertx-*-SNAPSHOT.jar create-database
java -jar target/vertx-*-SNAPSHOT.jar run
```

If you want to wipe out the embedded database, just delete the data files.

```
rm -rf ./hsql
```

You can combine the above to quickly reset and restart everything.

```
rm -rf .hsql ; mvn -DskipTests clean package ; java -jar target/vertx-*-SNAPSHOT.jar create-database run
```

Before you commit your changes, run the static analysis checks. This will
make sure code is formatted correctly and doesn't contain certain kinds of
errors and security vulnerabilities. These are divided into two sets, only
because certain of the tools are mutually incompatible.

```
mvn -Pchecks clean verify
mvn -DskipTests -Dcheck1 clean verify
mvn -DskipTests -Dcheck2 clean verify
```
