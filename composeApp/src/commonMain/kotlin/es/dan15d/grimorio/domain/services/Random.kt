package es.dan15d.grimorio.domain.services

import kotlin.math.floor

/** Fuente de aleatoriedad inyectable (en los tests se usa una con semilla). Devuelve un número en [0, 1). */
typealias RandomFn = () -> Double

fun <T> shuffle(items: List<T>, random: RandomFn): List<T> {
    val out = items.toMutableList()
    for (i in out.size - 1 downTo 1) {
        val j = floor(random() * (i + 1)).toInt()
        val tmp = out[i]
        out[i] = out[j]
        out[j] = tmp
    }
    return out
}

fun <T> pick(items: List<T>, random: RandomFn): T? =
    if (items.isNotEmpty()) items[floor(random() * items.size).toInt()] else null

/**
 * Generador con semilla (mulberry32) para pruebas reproducibles. Da la misma secuencia que la web:
 * la aritmética de 32 bits de JavaScript (Math.imul, >>>) es la de Int con desbordamiento.
 */
fun seededRandom(seed: Int): RandomFn {
    var a = seed
    return {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
    }
}

/** Kotlin no tiene el Math.random() de la web: una fuente de aleatoriedad normal. */
val defaultRandom: RandomFn = { kotlin.random.Random.nextDouble() }
