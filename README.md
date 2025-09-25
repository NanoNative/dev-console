# Nano Dev Console Service

The **Nano Dev Console** is a lightweight service that can be integrated into any Nano-based application to provide runtime visibility into system information and events.

---

## Importing the Dependency

Run `mvn clean install` from the root of this project. Once the artifact is installed in local Maven repository, it can be added as a dependency in your Nano application like below:
### Maven
<dependency>
    <groupId>org.nanonative.ab</groupId>
    <artifactId>nano-dev-console</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

---

## Configuration

The Dev Console supports two configurable properties that can be overridden:

- **CONFIG_DEV_CONSOLE_MAX_EVENTS** - Specifies the maximum number of events to be retained in memory.
- **CONFIG_DEV_CONSOLE_URL** - Specifies the URL path where the Dev Console can be accessed. 
Example config value for CONFIG_DEV_CONSOLE_URL: */alex* - The UI will be accessible at */dev-console/alex*
If no configuration is provided, the default url is */dev-console/ui*
