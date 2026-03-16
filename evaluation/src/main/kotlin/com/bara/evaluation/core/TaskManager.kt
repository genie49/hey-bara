package com.bara.evaluation.core

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import java.io.File
import java.net.URI

/**
 * YAML 태스크 파일 로더
 *
 * 지정된 리소스 디렉터리에서 .yaml 파일을 찾아 Task 객체로 역직렬화한다.
 * "roleplay" 하위 디렉터리는 제외한다.
 */
class TaskManager(private val resourceDir: String = "tasks") {

    private val yaml = Yaml(configuration = YamlConfiguration(
        strictMode = false,
        yamlNamingStrategy = YamlNamingStrategy.SnakeCase,
    ))

    /** 리소스 디렉터리 아래의 모든 태스크를 로드 */
    fun loadAll(): List<Task> {
        val resourceUrl = javaClass.classLoader.getResource(resourceDir)
            ?: error("Resource directory not found: $resourceDir")
        val dir = File(URI(resourceUrl.toString()))
        return dir.walk()
            .filter { it.isFile && it.extension == "yaml" }
            .filter { !it.path.contains("roleplay") }
            .map { file ->
                yaml.decodeFromString(Task.serializer(), file.readText())
            }
            .toList()
    }

    /** 특정 ID의 태스크만 로드 */
    fun load(taskId: String): Task? = loadAll().find { it.id == taskId }

    /** 카테고리별로 필터링 */
    fun loadByCategory(category: String): List<Task> =
        loadAll().filter { it.metadata.category == category }

    /** 태그로 필터링 */
    fun loadByTag(tag: String): List<Task> =
        loadAll().filter { tag in it.metadata.tags }
}
