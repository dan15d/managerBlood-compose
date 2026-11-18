package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameLogEntry
import es.dan15d.grimorio.domain.entities.GameTimer
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.services.clearTimer
import es.dan15d.grimorio.domain.services.noteInfoGiven
import es.dan15d.grimorio.domain.services.phaseStart
import es.dan15d.grimorio.domain.services.setTimer
import es.dan15d.grimorio.domain.services.startDay
import es.dan15d.grimorio.domain.services.startNight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// Cronómetro de la fase, cuenta atrás y apunte de la información dada.

private fun clockGame(phase: Phase = Phase.day(1)): Game =
    newGame(phase = phase, scriptId = "", scriptName = "", createdAt = "2026-11-05T10:00:00.000Z")

class ClockTest {
    @Test
    fun alCambiarDeFaseSeApuntaCuandoEmpiezaYSeQuitaLaCuentaAtras() {
        val before = Clock.System.now().toEpochMilliseconds()
        val night = startNight(setTimer(clockGame(), 60))
        assertNull(night.timer)
        assertTrue(Instant.parse(night.phaseStartedAt!!).toEpochMilliseconds() >= before)
        val day = startDay(setTimer(night, 60))
        assertNull(day.timer)
        assertTrue(Instant.parse(day.phaseStartedAt!!) >= Instant.parse(night.phaseStartedAt!!))
    }

    @Test
    fun enPartidasDeAntesElInicioDeLaFaseLoDiceElRegistro() {
        val g = clockGame(Phase.night(2)).copy(
            log = listOf(
                GameLogEntry("2026-11-05T10:10:00.000Z", "Día 1", "J1 es ejecutado."),
                GameLogEntry("2026-11-05T10:20:00.000Z", "Noche 2", "Cae la noche."),
                GameLogEntry("2026-11-05T10:21:00.000Z", "Noche 2", "J2 muere por Diablillo"),
            ),
        )
        assertEquals("2026-11-05T10:20:00.000Z", phaseStart(g))
        assertEquals("2026-11-05T10:00:00.000Z", phaseStart(clockGame()))
        assertEquals("2026-11-05T11:00:00.000Z", phaseStart(clockGame().copy(phaseStartedAt = "2026-11-05T11:00:00.000Z")))
    }

    @Test
    fun laCuentaAtrasAcabaCuandoTocaYSePuedeParar() {
        val now = Instant.parse("2026-11-05T12:00:00.000Z")
        val g = setTimer(clockGame(), 180, now)
        assertEquals(GameTimer(endsAt = "2026-11-05T12:03:00.000Z", seconds = 180), g.timer)
        assertEquals(0, g.log.size)
        val cleared = clearTimer(g)
        assertNull(cleared.timer)
        assertSame(cleared, clearTimer(cleared))
    }

    @Test
    fun laInformacionDadaQuedaEnElRegistroUnaLineaPorJugador() {
        val g = noteInfoGiven(clockGame(Phase.night(1)), listOf("Empático (J3): 1", "Empático (J5): 0"))
        assertEquals(listOf("Empático (J3): 1", "Empático (J5): 0"), g.log.map { it.text })
        assertEquals("Noche 1", g.log[0].phase)
    }
}
