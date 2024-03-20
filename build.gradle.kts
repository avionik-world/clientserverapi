import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

/*
 * MIT License
 *
 * Copyright (C) 2020 Frederick Baier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

plugins {
    id("java")
    kotlin("jvm") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    alias(libs.plugins.sonatypeCentralPortalPublisher)
}

group = "world.avionik"
version = "4.2.1"

repositories {
    mavenCentral()
    maven("https://repo.thesimplecloud.eu/artifactory/list/gradle-release-local/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.reflections:reflections:0.10.2")
    implementation("io.netty:netty-all:4.1.100.Final")
    implementation("commons-io:commons-io:2.14.0")
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("eu.thesimplecloud.jsonlib:json-lib:1.0.8")

    testImplementation("junit:junit:4.13.1")
    testImplementation("org.mockito:mockito-core:4.1.0")
}

tasks.named("shadowJar", ShadowJar::class) {
    mergeServiceFiles()
}

signing {
    useGpgCmd()
    sign(configurations.archives.get())
}


centralPortal {
    username = project.findProperty("sonatypeUsername") as String
    password = project.findProperty("sonatypePassword") as String

    pom {
        name.set("Clientserver API")
        description.set("Simplified Netty API")
        url.set("https://github.com/avionik-world/clientserverapi")

        developers {
            developer {
                id.set("niklasnieberler")
                email.set("admin@avionik.world")
            }
        }
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        scm {
            url.set("https://github.com/avionik-world/clientserverapi.git")
            connection.set("git:git@github.com:avionik-world/clientserverapi.git")
        }
    }
}