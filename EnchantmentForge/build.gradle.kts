plugins {
    java
}
group = "dev.enchantmentforge"
version = "1.3.0"
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
    archiveFileName.set("EnchantmentForge-1.3.0.jar")
}
