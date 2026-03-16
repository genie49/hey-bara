plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.bara.evaluation.MainKt")
}

dependencies {
    // Koog Agent SDK
    implementation(libs.koog.agents)

    // Google Generative AI
    implementation(libs.google.genai)

    // YAML
    implementation(libs.kaml)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // CLI
    implementation(libs.clikt)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
