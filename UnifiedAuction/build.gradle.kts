plugins { java }
group = "dev.unified"
version = "1.0.0"
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
dependencies { compileOnly("io.papermc.paper:paper-api:26.3.build.+") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.jar { archiveFileName.set("UnifiedAuction-1.0.0.jar") }
