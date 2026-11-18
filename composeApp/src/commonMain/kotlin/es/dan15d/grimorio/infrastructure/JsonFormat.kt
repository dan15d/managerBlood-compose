package es.dan15d.grimorio.infrastructure

import kotlinx.serialization.json.Json

/**
 * El JSON con que se guardan la partida, los guiones y los ajustes: el mismo formato que la web.
 * Los campos opcionales sin valor no se escriben (como `undefined` en la web) y los que no
 * conoce esta versión de la app se ignoran al leer.
 */
val GrimJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
