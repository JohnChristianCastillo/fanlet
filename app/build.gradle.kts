import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.johncastles.fanlet"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.johncastles.fanlet"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// ---------------------------------------------------------------------------
// Permission allowlist
//
// The build fails if the merged release manifest (the app's own manifest plus
// everything contributed by libraries) requests a permission that is not listed
// here. Adding a permission is therefore always a visible, reviewable change to
// this file. Runs as part of `./gradlew check`.
// ---------------------------------------------------------------------------

val permissionAllowlist = listOf(
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.VIBRATE",
)

// Rejected even if someone adds them to the allowlist above.
val permissionDenylist = listOf(
    "android.permission.INTERNET",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.PACKAGE_USAGE_STATS",
)

val permissionReport = layout.buildDirectory.file("reports/manifest-permissions/release.txt")

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        tasks.register<CheckManifestPermissionsTask>("checkReleaseManifestPermissions") {
            group = "verification"
            description = "Fails if the merged release manifest requests a permission outside the allowlist."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowedPermissions.addAll(permissionAllowlist)
            // AndroidX core declares and uses this app-private permission to protect
            // receivers registered with RECEIVER_NOT_EXPORTED on older Android versions.
            allowedPermissions.add(
                variant.applicationId.map { "$it.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" }
            )
            forbiddenPermissions.addAll(permissionDenylist)
            report.set(permissionReport)
        }
    }
}

tasks.named("check") {
    dependsOn("checkReleaseManifestPermissions")
}

abstract class CheckManifestPermissionsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val allowedPermissions: SetProperty<String>

    @get:Input
    abstract val forbiddenPermissions: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun checkPermissions() {
        val manifestFile = mergedManifest.get().asFile
        val requested = readRequestedPermissions(manifestFile)

        val forbidden = requested.filter { it in forbiddenPermissions.get() }
        val notAllowed = requested.filter { it !in allowedPermissions.get() && it !in forbidden }

        if (forbidden.isNotEmpty() || notAllowed.isNotEmpty()) {
            val message = buildString {
                appendLine("The merged release manifest requests permissions that are not allowed.")
                forbidden.forEach { appendLine("  forbidden:        $it") }
                notAllowed.forEach { appendLine("  not on allowlist: $it") }
                appendLine("Manifest: ${manifestFile.absolutePath}")
                append("The allowlist is defined in app/build.gradle.kts.")
            }
            throw GradleException(message)
        }

        report.get().asFile.writeText(requested.sorted().joinToString(separator = "\n", postfix = "\n"))
    }

    private fun readRequestedPermissions(manifestFile: java.io.File): List<String> {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val document = factory.newDocumentBuilder().parse(manifestFile)

        val permissionTags = listOf("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")
        return permissionTags.flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map { index ->
                (nodes.item(index) as Element).getAttributeNS(androidNamespace, "name")
            }
        }.distinct()
    }
}
