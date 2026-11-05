# Gradle wrapper

This project includes a small bootstrap `gradle-wrapper.jar` built from
`tools/gradle-wrapper-src/org/gradle/wrapper/GradleWrapperMain.java`.

It downloads the official Gradle 8.9 distribution declared in
`gradle/wrapper/gradle-wrapper.properties` into the normal user cache
(`GRADLE_USER_HOME`, or `~/.gradle`) and then launches
`org.gradle.launcher.GradleMain`. It does not modify the Gradle distribution.

If you prefer the stock wrapper, install Gradle 8.9 once and run:

```bash
gradle wrapper --gradle-version 8.9
```

That replaces `gradle-wrapper.jar` with the official one. Android Studio only
needs a working wrapper; both variants are compatible with this project.
