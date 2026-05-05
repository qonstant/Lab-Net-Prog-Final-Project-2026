plugins {
    base
}

repositories {
    mavenCentral()
}

val basyxVersion = providers.gradleProperty("basyxVersion").orElse("1.4.0")
val runtimeDeps by configurations.creating

dependencies {
    runtimeDeps("org.eclipse.basyx:basyx.components.registry:${basyxVersion.get()}")
}

tasks.register<Copy>("copyRuntimeDeps") {
    from(runtimeDeps)
    into(layout.buildDirectory.dir("runtime-libs"))
}
