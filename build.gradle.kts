plugins {
    java
}
group = "dev.targetclaim"
version = "1.4.4"
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
tasks.jar {
    archiveFileName.set("TargetClaim-1.4.4.jar")
}
