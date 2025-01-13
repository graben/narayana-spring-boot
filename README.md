[![Version](https://img.shields.io/maven-central/v/dev.snowdrop/narayana-spring-boot-parent?logo=apache-maven&style=for-the-badge)](https://search.maven.org/artifact/dev.snowdrop/narayana-spring-boot-parent)
[![GitHub Actions Status](<https://img.shields.io/github/actions/workflow/status/snowdrop/narayana-spring-boot/test.yml?branch=main&logo=GitHub&style=for-the-badge>)](https://github.com/snowdrop/narayana-spring-boot/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/snowdrop/narayana-spring-boot?style=for-the-badge&logo=apache)](https://www.apache.org/licenses/LICENSE-2.0)

# Narayana Spring Boot

Narayana is a popular open source JTA transaction manager implementation supported by Red Hat.
You can use the `narayana-spring-boot-starter` starter to add the appropriate Narayana dependencies to your project.
Spring Boot automatically configures Narayana and post-processes your beans to ensure that startup and shutdown ordering
is correct.

```xml
<dependency>
    <groupId>dev.snowdrop</groupId>
    <artifactId>narayana-spring-boot-starter</artifactId>
    <version>RELEASE</version>
</dependency>
```

By default, Narayana transaction logs are written to a `transaction-logs` directory in your application home directory
(the directory in which your application jar file resides). You can customize the location of this directory by setting
a `narayana.log-dir` property in your application.properties file. Properties starting with `narayana` can also be used
to customize the Narayana configuration. See the
[NarayanaProperties](narayana-spring-boot-core/src/main/java/dev/snowdrop/boot/narayana/core/properties/NarayanaProperties.java)
Javadoc for complete details.

> Only a limited number of Narayana configuration options are exposed via `application.properties`. For a more complex
configuration you can provide a `jbossts-properties.xml` file. To get more details, please, consult
Narayana project [documentation](http://narayana.io/docs/project/index.html).

> To ensure that multiple transaction managers can safely coordinate the same resource managers, each Narayana instance
must be configured with a unique ID. By default, this ID is set to 1. To ensure uniqueness in production, you should
configure the `narayana.node-identifier` property with a different value for each instance of your application. This value
must not exceed a length of 28 bytes. To ensure that the value is shortened to a valid length by hashing with SHA-224 and encoding
with base64, configure `narayana.shorten-node-identifier-if-necessary` property to true. Be aware, this may result in duplicate
strings which break the uniqueness that is mandatory for safe transaction usage!

# Batch application

If you are running your Spring Boot application as a batch program, you'll have to explicitly call exit (`SIGTERM`) on your application to proper shutdown.
This is needed because of Narayana is running periodic recovery in a non-daemon background thread.

This could be achieved with the following code example:
```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.exit(SpringApplication.run(Application.class, args));
    }
}
```

# Using databases

By default, [Narayana Transactional driver](https://www.narayana.io/docs/api/com/arjuna/ats/jdbc/TransactionalDriver.html)
is used to enlist a relational database to a JTA transaction which provides a basic XAResource enlistment and recovery as
well as a simple pooling mechanism which is disabled as default. See [TransactionalDriverProperties](narayana-spring-boot-core/src/main/java/dev/snowdrop/boot/narayana/core/properties/TransactionalDriverProperties.java)
for more details.

> Be aware that Narayana Transactional driver automatically set transaction isolation level to `Connection.TRANSACTION_SERIALIZABLE`,
which might change default behaviour of the used database system!
For example, [Oracle Database](narayana-spring-boot-starter-it/src/test/resources/oracle-initscript.sql)

## Add pooling

If you need a more sophisticated connection management, we advise you to use [agroal-spring-boot-starter](https://agroal.github.io)
which provides connection pooling and many other features. To enable Agroal add the following dependency to your application configuration:
```xml
<dependency>
    <groupId>io.agroal</groupId>
    <artifactId>agroal-spring-boot-starter</artifactId>
    <version>2.x.x</version>
</dependency>
```

All Agroal configuration properties described in its [documentation](https://agroal.github.io/docs.html)

# Using messaging brokers

This Narayana starter supports two ways to enlist a messaging broker to a JTA transaction: plain connection
factory and MessagingHub pooled connection factory.

By default, [Narayana Connection Proxy](https://www.narayana.io/docs/api/org/jboss/narayana/jta/jms/ConnectionFactoryProxy.html)
around the JMS connection factory is used which provides a basic XAResource enlistment and recovery.

## Add pooling

If you need a more sophisticated connection management, you can enable MessagingHub support which provides connection pooling
and many other features. To enable MessagingHub add the following dependency and property to you application configuration:
```xml
<dependency>
    <groupId>org.messaginghub</groupId>
    <artifactId>pooled-jms</artifactId>
</dependency>
```
```properties
narayana.messaginghub.enabled=true
```

All MessagingHub configuration properties described in its [documentation](https://github.com/messaginghub/pooled-jms/blob/master/pooled-jms-docs/Configuration.md)
are mapped with a prefix `narayana.messaginghub`. So for example if you'd like to set a max connections pool size to 10,
you could do that by adding this entry to your application configuration:
```properties
narayana.messaginghub.maxConnections=10
```

# Release

## Manually

Dry run:
```sh
mvn release:prepare -DdryRun
```

Tag:
```sh
mvn release:prepare
```

Deploy:
```sh
mvn release:perform
```

Set all modules to new SNAPSHOT version:
```sh
mvn versions:set
mvn versions:commit
```
