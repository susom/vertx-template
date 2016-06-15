Getting Started
=========

#### What You Need

[Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
and [Maven 3](https://maven.apache.org/).

Some of the libraries referenced by this project are currently private to
IRT, so you will need to have Maven configured to use our internal repository
(see https://medwiki.stanford.edu/display/kbase/Maven).

#### Run Locally

Copy the default properties for development. The defaults will work with an
embedded database. You will need to adjust them if you use a different database.

```
cp sample.properties local.properties
```

Create a private key for session key encryption with JWT.

```
keytool -genseckey -keystore local.jwt.jceks -storetype jceks -storepass secret \
        -keyalg HMacSHA256 -keysize 2048 -alias HS256 -keypass secret
```

Build everything, create the database schema, and start the server.

```
mvn package
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
errors and security vulnerabilities.

```
mvn -Pchecks clean verify
```
