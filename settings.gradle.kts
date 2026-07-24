plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "KIndex"

include("kindex-cli")
include("kindex-core")
include("kindex-parser")
include("kindex-storage")