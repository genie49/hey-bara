package com.bara.evaluation.core

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import java.io.File
import java.net.URI

/**
 * 롤플레이 시나리오 YAML 로더
 *
 * 지정된 리소스 디렉터리의 "roleplay" 하위 디렉터리에서
 * .yaml 파일을 찾아 Scenario 객체로 역직렬화한다.
 */
class ScenarioManager(private val basePath: String = "tasks") {

    private val yaml = Yaml(configuration = YamlConfiguration(
        strictMode = false,
        yamlNamingStrategy = YamlNamingStrategy.SnakeCase,
    ))

    /** roleplay 하위 디렉터리의 모든 시나리오를 로드 */
    fun loadAll(): List<Scenario> {
        val resource = this::class.java.classLoader.getResource(basePath) ?: return emptyList()
        val dir = File(URI(resource.toString()))
        return dir.walkTopDown()
            .filter { it.path.contains("roleplay") }
            .filter { it.isFile && (it.extension == "yaml" || it.extension == "yml") }
            .map { yaml.decodeFromString(Scenario.serializer(), it.readText()) }
            .toList()
    }

    /** 특정 에이전트 ID로 시작하는 시나리오만 로드 */
    fun loadByAgent(agent: String): List<Scenario> = loadAll().filter { it.id.startsWith(agent) }

    /** 특정 ID의 시나리오만 로드 */
    fun loadById(id: String): Scenario? = loadAll().find { it.id == id }
}
