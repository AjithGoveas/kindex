plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    val nativeTargets = listOf(
        mingwX64(),
        linuxX64(),
        macosArm64(),
        macosX64()
    )

    applyDefaultHierarchyTemplate()

//    nativeTargets.forEach { target ->
//        target.compilations.getByName("main") {
//            val treesitter by cinterops.creating {
//                defFile(project.file("src/nativeInterop/cinterop/treesitter.def"))
//                includeDirs(project.file("src/nativeInterop/cinterop/include"))
//            }
//        }
//    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kindex-core"))
        }
        jvmMain.dependencies {
            implementation(libs.tree.sitter)
            implementation(libs.tree.sitter.java)
            implementation(libs.tree.sitter.kotlin)
            implementation(libs.tree.sitter.rust)
            implementation(libs.tree.sitter.typescript)
            implementation(libs.tree.sitter.go)
            implementation(libs.tree.sitter.c)
            implementation(libs.tree.sitter.cpp)
            implementation(libs.tree.sitter.c.sharp)
            implementation(libs.tree.sitter.javascript)
            implementation(libs.tree.sitter.css)
        }
    }
}
