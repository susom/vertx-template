Getting Started
=========

#### What You Need

Java 8 and Maven 3.

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
