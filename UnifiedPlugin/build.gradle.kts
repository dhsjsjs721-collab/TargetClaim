plugins { java }
group = "dev.unified"
version = "1.1.22"

repositories { maven("https://repo.papermc.io/repository/maven-public/") }
dependencies { compileOnly("io.papermc.paper:paper-api:26.3.build.+") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
sourceSets {
main {
java.srcDir("build/generated-sources")
resources.srcDir("build/generated-resources")
}
}
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.jar { archiveFileName.set("MinecraftUnified-1.1.22.jar") }
