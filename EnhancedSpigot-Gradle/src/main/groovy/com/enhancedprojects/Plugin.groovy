/*
 * Copyright 2026 KPG-TB
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.enhancedprojects


import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

class Plugin implements org.gradle.api.Plugin<Project> {
    @Override
    void apply(Project project) {
        def ext = project.extensions.create("enhancedspigot", Extension);
        ext.relocationPath.convention("")
        ext.excludeDependencies.convention(true)
        ext.importVault.convention(false);
        ext.importPapi.convention(false);
        ext.modulesVersion.convention("3.0.0-SNAPSHOT")
        ext.enabledModules.convention([])
        ext.overrideVersions.convention(Map.of())

        project.getPluginManager().apply("io.freefair.lombok")
        project.getPluginManager().apply("com.gradleup.shadow")

        project.tasks.register("enhancedSpigotLibraries", LibrariesTask) {
            group = 'enhancedspigot'
            description = 'Print all libraries required in plugin.yml'
            enabledModules.set(ext.enabledModules)
            excludeDependencies.set(ext.excludeDependencies)
        }

        project.afterEvaluate {
            project.repositories {
                mavenCentral()
                mavenLocal()
                maven { url "https://nexus.enhancedprojects.com/repository/maven-snapshots" }
                if (ext.enabledModules.get().contains(EnhancedModule.Menus)) maven { url 'https://mvn.wesjd.net/' }
                if (ext.importVault.get()) maven { url 'https://jitpack.io' }
                if (ext.importPapi.get()) maven { url = 'https://repo.extendedclip.com/releases/' }
            }

            project.dependencies {
                List<EnhancedModule> enabledModules = ext.enabledModules.get()
                Map<EnhancedModule, String> overrideVersions = ext.overrideVersions.get()

                for (final def module in EnhancedModule.values()) {
                    if (enabledModules.contains(module)) implementation "com.enhancedprojects:EnhancedSpigot-" + module.name() + ":" + (
                            overrideVersions.containsKey(module) ? overrideVersions.get(module) : ext.modulesVersion.get()
                    )
                }

                if (ext.importVault.get()) compileOnly "com.github.MilkBowl:VaultAPI:1.7"
                if (ext.importPapi.get()) compileOnly 'me.clip:placeholderapi:2.11.7'
            }

            project.configurations.configureEach {
                resolutionStrategy.capabilitiesResolution.withCapability("org.bukkit:bukkit") {
                    selectHighestVersion()
                }
            }

            project.tasks.withType(JavaCompile).configureEach {
                if (ext.enabledModules.get().contains(EnhancedModule.Commands)) options.compilerArgs << "-parameters"
            }

            project.tasks.named("shadowJar").configure {
                relocate(
                        "com.enhancedprojects.enhancedspigot",
                        ext.relocationPath.get().isEmpty() ?
                                project.group.toString() + "." + project.name.toLowerCase() + ".lib"
                                : ext.relocationPath.get()
                )

                if (ext.excludeDependencies.get()) {
                    exclude("com/j256/ormlite/**")
                    exclude("org/postgresql/**")
                    exclude("com/zaxxer/**")
                    exclude("org/slf4j/**")
                    exclude("org/checkerframework/**")
                    exclude("com/ezylang/**")
                }

                archiveFileName = project.name + "-" + project.version.toString() + ".jar"
            }
        }
    }
}
