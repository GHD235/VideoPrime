/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "NewPipe"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.clojars.org")
    }
}
include(":app") // androidApp
include(":desktopApp")
include("shared")

// VideoPrime: the extractor is our fork (GHD235/NewPipeExtractor, branch videoprime), checked out
// as the git submodule NewPipeExtractor/ and built together with the app instead of via JitPack.
// Clone with `git clone --recurse-submodules` or run `git submodule update --init`.
if (file("NewPipeExtractor/settings.gradle.kts").exists()) {
    includeBuild("NewPipeExtractor") {
        dependencySubstitution {
            substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
                .using(project(":extractor"))
        }
    }
}

// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that NewPipe and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().

//    includeBuild("../NewPipeExtractor") {
//        dependencySubstitution {
//            substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
//                .using(project(":extractor"))
//        }
//    }
