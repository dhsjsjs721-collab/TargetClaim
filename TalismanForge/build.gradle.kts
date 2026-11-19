plugins { java }
group = "dev.talismanforge"
version = "2.4.0"
repositories { maven("https://repo.papermc.io/repository/maven-public/") }
dependencies { compileOnly("io.papermc.paper:paper-api:26.3.build.+") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.jar { archiveFileName.set("TalismanForge-2.4.0.jar") }
