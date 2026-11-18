package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.services.APP_REMINDER
import es.dan15d.grimorio.domain.services.CHOICE_REMINDER
import es.dan15d.grimorio.domain.services.CHOICE_WITHOUT_REMINDER
import es.dan15d.grimorio.domain.services.DUSK_REMINDERS
import es.dan15d.grimorio.domain.services.REMINDER
import es.dan15d.grimorio.domain.services.disablesAbility
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Las reglas leen el estado del Grimorio en los textos de los recordatorios. Si un texto cambia
// en characters.json y no en el código (o al revés), la regla deja de funcionar sin avisar:
// estos tests lo detectan.

private val allLabels: Set<String> by lazy { TestCatalog.all.flatMap { it.reminders }.toSet() }
private fun labelsOf(id: String) = TestCatalog.get(id)?.reminders ?: emptyList()
private val appLabels: Set<String> = APP_REMINDER.all.values.toSet()

class RemindersTest {
    @Test
    fun cadaTextoDeReminderExisteEnElCatalogo() {
        for ((key, label) in REMINDER.all) assertTrue(label in allLabels, "$key: «$label»")
    }

    @Test
    fun losQuePoneLaAppNoSeConfundenConLosDelCatalogo() {
        for (label in appLabels) assertFalse(label in allLabels, "«$label»")
    }

    @Test
    fun loQuePoneQuienEligeDeNocheEsUnRecordatorioDeEsePersonaje() {
        for ((id, label) in CHOICE_REMINDER) {
            assertNotNull(TestCatalog.get(id), id)
            // Los que no ponen recordatorio (Pit-Hag, Soñador…): el texto solo sirve para tocar al jugador.
            if (id in CHOICE_WITHOUT_REMINDER) continue
            assertContains(labelsOf(id), label, "$id: «$label»")
        }
    }

    @Test
    fun losQueCaducanAlAnochecerSonRecordatoriosDeSuPersonaje() {
        // «Borracho» del Juglar lo pone la app: al amanecer, «Borracho hasta mañana» pasa a «Borracho».
        val madeByApp = setOf("minstrel:Borracho")
        for (key in DUSK_REMINDERS) {
            val id = key.substringBefore(":")
            val label = key.substringAfter(":")
            if (id == "custom" || key in madeByApp) continue
            assertContains(labelsOf(id), label, key)
        }
    }

    @Test
    fun loQueDejaSinHabilidad() {
        for (label in listOf("Envenenado", "Borracho", "Borracho 2", "Es el Borracho", "Todos están borrachos", "Sin habilidad", APP_REMINDER.minstrelTomorrow)) {
            assertTrue(disablesAbility(label), "«$label» la quita")
        }
    }

    @Test
    fun laLocuraYOtrosNoQuitanLaHabilidad() {
        // La locura no quita la habilidad: el jugador loco la conserva.
        for (label in listOf("Loco", "Tiene habilidad", "Sobrio y sano", "A salvo", "Muerto")) {
            assertFalse(disablesAbility(label), "«$label» no la quita")
        }
    }
}
