/*
 * Copyright 2019 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.gradle.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

abstract class MRGenerator(
    generatedDir: File,
    protected val sourceSet: SourceSet,
    private val mrClassPackage: String,
    private val generators: List<Generator>
) {
    private val sourcesGenerationDir = File(generatedDir, "${sourceSet.name}/src")
    protected val resourcesGenerationDir = File(generatedDir, "${sourceSet.name}/res")

    init {
        sourcesGenerationDir.mkdirs()
        sourceSet.addSourceDir(sourcesGenerationDir)

        resourcesGenerationDir.mkdirs()
        sourceSet.addResourcesDir(resourcesGenerationDir)
    }

    private fun generate() {
        sourcesGenerationDir.deleteRecursively()
        resourcesGenerationDir.deleteRecursively()

        @Suppress("SpreadOperator")
        val mrClassSpec = TypeSpec.objectBuilder(mrClassName)
            .addModifiers(*getMRClassModifiers())

        generators.forEach { mrClassSpec.addType(it.generate(resourcesGenerationDir)) }

        processMRClass(mrClassSpec)

        val mrClass = mrClassSpec.build()

        val fileSpec = FileSpec.builder(mrClassPackage, mrClassName)
            .addType(mrClass)

        generators
            .flatMap { it.getImports() }
            .plus(getImports())
            .forEach { fileSpec.addImport(it.packageName, it.simpleName) }

        val file = fileSpec.build()
        file.writeTo(sourcesGenerationDir)
    }

    fun apply(project: Project) {
        val name = sourceSet.name
        val genTaskName = "generateMR$name"
        val genTask = runCatching {
            project.tasks.getByName(genTaskName)
        }.getOrNull() ?: project.task(genTaskName) {
            group = "multiplatform"

            doLast {
                this@MRGenerator.generate()
            }
        }

        apply(generationTask = genTask, project = project)
    }

    protected abstract fun getMRClassModifiers(): Array<KModifier>
    protected abstract fun apply(generationTask: Task, project: Project)

    protected open fun processMRClass(mrClass: TypeSpec.Builder) {}
    protected open fun getImports(): List<ClassName> = emptyList()

    private companion object {
        const val mrClassName = "MR"
    }

    interface Generator {
        fun generate(resourcesGenerationDir: File): TypeSpec
        fun getImports(): List<ClassName>
    }

    interface SourceSet {
        val name: String

        fun addSourceDir(directory: File)
        fun addResourcesDir(directory: File)
    }
}
