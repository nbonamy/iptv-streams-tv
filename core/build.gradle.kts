plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
sourceSets["test"].resources.srcDir(rootProject.file("app/src/main/assets"))
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.12.1")
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.register<JavaExec>("compileBundledCatalog") {
    dependsOn(tasks.named("classes"))
    val playlist = rootProject.file("app/src/main/assets/worldwide.m3u")
    val metadata = rootProject.file("app/src/main/assets/channels.json")
    val output = rootProject.layout.buildDirectory.file("generated/bundled-catalog/catalog.json")
    inputs.files(playlist, metadata)
    inputs.files(sourceSets["main"].output)
    outputs.file(output)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("fr.bonamy.iptv.core.BundledCatalogCompiler")
    args(playlist.absolutePath, metadata.absolutePath, output.get().asFile.absolutePath)
}
