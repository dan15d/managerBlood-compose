package es.dan15d.grimorio.infrastructure.catalog

import es.dan15d.grimorio.application.ports.CharacterCatalog
import es.dan15d.grimorio.application.ports.OfficialScriptSource
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Jinx
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.infrastructure.GrimJson
import es.dan15d.grimorio.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Datos en español de la wiki de Villacuervos (villacuervos.es/wiki) con el orden de noche oficial
// de The Pandemonium Institute, y las incompatibilidades (jinxes) oficiales de la misma wiki.
// Se generan con un script aparte en la web; no editar a mano. Están en composeResources/files/data.
//
// Los iconos (Character.image, p. ej. "roles/mayor.webp") son rutas relativas a composeResources/files:
// la presentación las resuelve con Res.getUri("files/" + image). Las URL (http…) se dejan tal cual.

class StaticCharacterCatalog(
    private val list: List<Character>,
    private val jinxList: List<Jinx>,
) : CharacterCatalog {
    private val byId: Map<String, Character> = list.associateBy { it.id }

    override fun jinxes(): List<Jinx> = jinxList

    override fun all(): List<Character> = list

    override fun get(id: String): Character? = byId[id]

    override fun map(): Map<String, Character> = byId
}

class StaticOfficialScripts(private val scripts: List<Script>) : OfficialScriptSource {
    override fun list(): List<Script> = scripts
}

internal suspend fun readDataFile(name: String): String = Res.readBytes("files/data/$name").decodeToString()

private val loading = Mutex()
private var catalog: StaticCharacterCatalog? = null
private var officialScripts: StaticOfficialScripts? = null

/** Carga el catálogo de personajes (una sola vez: las siguientes devuelven el mismo). Se llama al arrancar. */
suspend fun loadCatalog(): StaticCharacterCatalog = loading.withLock {
    catalog ?: StaticCharacterCatalog(
        list = GrimJson.decodeFromString<List<Character>>(readDataFile("characters.json")),
        jinxList = GrimJson.decodeFromString<List<Jinx>>(readDataFile("jinxes.json")),
    ).also { catalog = it }
}

/** Carga los guiones oficiales (una sola vez). */
suspend fun loadOfficialScripts(): StaticOfficialScripts = loading.withLock {
    officialScripts ?: StaticOfficialScripts(GrimJson.decodeFromString<List<Script>>(readDataFile("scripts.json")))
        .also { officialScripts = it }
}
