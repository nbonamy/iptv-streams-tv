plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
sourceSets["test"].resources.srcDir(rootProject.file("app/src/main/assets"))
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.12.1")
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
