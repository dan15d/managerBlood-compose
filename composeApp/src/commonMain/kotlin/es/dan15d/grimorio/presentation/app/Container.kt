package es.dan15d.grimorio.presentation.app

import es.dan15d.grimorio.application.ports.AiTransport
import es.dan15d.grimorio.application.ports.AppSettings
import es.dan15d.grimorio.application.ports.CharacterCatalog
import es.dan15d.grimorio.application.ports.CustomScriptRepository
import es.dan15d.grimorio.application.ports.GameRepository
import es.dan15d.grimorio.application.ports.SettingsStore
import es.dan15d.grimorio.application.ports.TablePublisher
import es.dan15d.grimorio.application.usecases.AdminSession
import es.dan15d.grimorio.application.usecases.AskAiAdvice
import es.dan15d.grimorio.application.usecases.CompleteManualSetup
import es.dan15d.grimorio.application.usecases.CreateGame
import es.dan15d.grimorio.application.usecases.DeleteCustomScript
import es.dan15d.grimorio.application.usecases.DeleteGame
import es.dan15d.grimorio.application.usecases.GenerateAiScript
import es.dan15d.grimorio.application.usecases.GenerateAiSetup
import es.dan15d.grimorio.application.usecases.ListGames
import es.dan15d.grimorio.application.usecases.ListScripts
import es.dan15d.grimorio.application.usecases.ListSpecialCases
import es.dan15d.grimorio.application.usecases.LoadGame
import es.dan15d.grimorio.application.usecases.ManageLobby
import es.dan15d.grimorio.application.usecases.ParseScriptJson
import es.dan15d.grimorio.application.usecases.PlayerTable
import es.dan15d.grimorio.application.usecases.ReadCharacterGuide
import es.dan15d.grimorio.application.usecases.ReviewAiScript
import es.dan15d.grimorio.application.usecases.SaveCustomScript
import es.dan15d.grimorio.application.usecases.SaveGame
import es.dan15d.grimorio.application.usecases.SuggestRandomSetup
import es.dan15d.grimorio.application.usecases.TestAiConnection
import es.dan15d.grimorio.application.usecases.WatchGame
import es.dan15d.grimorio.application.usecases.WriteAiChronicle
import es.dan15d.grimorio.domain.services.RandomFn
import es.dan15d.grimorio.infrastructure.ai.BrowserDeepSeekTransport
import es.dan15d.grimorio.infrastructure.ai.ChatTransport
import es.dan15d.grimorio.infrastructure.ai.LlmStoryteller
import es.dan15d.grimorio.infrastructure.ai.SupabaseFunctionTransport
import es.dan15d.grimorio.infrastructure.catalog.StaticCharacterGuides
import es.dan15d.grimorio.infrastructure.catalog.loadCatalog
import es.dan15d.grimorio.infrastructure.catalog.loadOfficialScripts
import es.dan15d.grimorio.infrastructure.config.Env
import es.dan15d.grimorio.infrastructure.config.LocalSettingsStore
import es.dan15d.grimorio.infrastructure.config.RandomIdGenerator
import es.dan15d.grimorio.infrastructure.config.hasSupabase
import es.dan15d.grimorio.infrastructure.persistence.LocalAdminAuth
import es.dan15d.grimorio.infrastructure.persistence.LocalStorageGameRepository
import es.dan15d.grimorio.infrastructure.persistence.LocalStorageScriptRepository
import es.dan15d.grimorio.infrastructure.persistence.NoTablePublisher
import es.dan15d.grimorio.infrastructure.persistence.SupabaseAdminAuth
import es.dan15d.grimorio.infrastructure.persistence.SupabaseGameRepository
import es.dan15d.grimorio.infrastructure.persistence.SupabaseLobbyGateway
import es.dan15d.grimorio.infrastructure.persistence.SupabasePlayerGateway
import es.dan15d.grimorio.infrastructure.persistence.SupabaseScriptRepository
import es.dan15d.grimorio.infrastructure.persistence.SupabaseTablePublisher
import es.dan15d.grimorio.infrastructure.persistence.createGrimorioSupabase
import io.github.jan.supabase.SupabaseClient
import kotlin.random.Random

// Raíz de composición: el único sitio que conoce las implementaciones concretas.
// Las pantallas solo usan los casos de uso que se exponen aquí.

/** Un solo cliente de Supabase para toda la app (si hay configuración; si no, todo en local). */
private val supabase: SupabaseClient? by lazy {
    if (hasSupabase()) createGrimorioSupabase(Env.supabaseUrl, Env.supabaseAnonKey) else null
}

/** Las guías guardan las interacciones ya leídas: una para toda la app. */
private val guides by lazy { StaticCharacterGuides() }

private fun transportFor(settings: AppSettings): ChatTransport? {
    val cloud = supabase
    if (settings.aiTransport == AiTransport.Cloud && cloud != null) return SupabaseFunctionTransport(cloud, settings.aiModel)
    if (settings.deepseekKey.isNotEmpty()) return BrowserDeepSeekTransport(settings.deepseekKey, settings.aiModel)
    return null
}

class Container(
    val catalog: CharacterCatalog,
    /** "supabase" o "local". */
    val storage: String,
    val aiAvailable: Boolean,
    val listScripts: ListScripts,
    val saveScript: SaveCustomScript,
    val deleteScript: DeleteCustomScript,
    val parseScriptJson: ParseScriptJson,
    val suggestRandomSetup: SuggestRandomSetup,
    val completeManualSetup: CompleteManualSetup,
    val generateAiSetup: GenerateAiSetup,
    val generateAiScript: GenerateAiScript,
    val createGame: CreateGame,
    val listGames: ListGames,
    val loadGame: LoadGame,
    val saveGame: SaveGame,
    val watchGame: WatchGame,
    val deleteGame: DeleteGame,
    val askAi: AskAiAdvice,
    val writeChronicle: WriteAiChronicle,
    val reviewAiScript: ReviewAiScript,
    val readGuide: ReadCharacterGuide,
    val listSpecialCases: ListSpecialCases,
    val testAi: TestAiConnection,
    val newId: () -> String,
    val admin: AdminSession,
    /** Sala y jugadores solo existen con la nube: sin ella, la app es solo del Narrador. */
    val lobby: ManageLobby?,
    val player: PlayerTable?,
)

/** Monta la app con los ajustes actuales. La primera vez carga el catálogo (después se reutiliza). */
suspend fun buildContainer(settings: AppSettings): Container {
    val catalog = loadCatalog()
    val officialScripts = loadOfficialScripts()
    val ids = RandomIdGenerator()
    val random: RandomFn = { Random.nextDouble() }
    val cloud = supabase
    val games: GameRepository = if (cloud != null) SupabaseGameRepository(cloud) else LocalStorageGameRepository()
    val scripts: CustomScriptRepository = if (cloud != null) SupabaseScriptRepository(cloud) else LocalStorageScriptRepository()
    val ai = LlmStoryteller(transportFor(settings))
    val publisher: TablePublisher = if (cloud != null) SupabaseTablePublisher(cloud) else NoTablePublisher()

    return Container(
        catalog = catalog,
        storage = if (cloud != null) "supabase" else "local",
        aiAvailable = ai.isAvailable(),
        listScripts = ListScripts(officialScripts, scripts),
        saveScript = SaveCustomScript(scripts, ids),
        deleteScript = DeleteCustomScript(scripts),
        parseScriptJson = ParseScriptJson(catalog),
        suggestRandomSetup = SuggestRandomSetup(catalog, random),
        completeManualSetup = CompleteManualSetup(catalog, random),
        generateAiSetup = GenerateAiSetup(catalog, ai, random),
        generateAiScript = GenerateAiScript(catalog, ai),
        createGame = CreateGame(games, catalog, ids, random, publisher),
        listGames = ListGames(games),
        loadGame = LoadGame(games),
        saveGame = SaveGame(games, publisher, catalog),
        watchGame = WatchGame(games),
        deleteGame = DeleteGame(games, publisher),
        askAi = AskAiAdvice(ai, catalog, guides),
        writeChronicle = WriteAiChronicle(ai, catalog),
        reviewAiScript = ReviewAiScript(catalog, ai),
        readGuide = ReadCharacterGuide(catalog, guides),
        listSpecialCases = ListSpecialCases(catalog, guides),
        testAi = TestAiConnection(ai),
        newId = { ids.uuid() },
        admin = AdminSession(if (cloud != null) SupabaseAdminAuth(cloud) else LocalAdminAuth()),
        lobby = if (cloud != null) ManageLobby(SupabaseLobbyGateway(cloud), ids) else null,
        player = if (cloud != null) PlayerTable(SupabasePlayerGateway(cloud)) else null,
    )
}

val settingsStore: SettingsStore = LocalSettingsStore()

val supabaseConfigured: Boolean get() = hasSupabase()
