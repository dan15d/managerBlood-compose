import type { ReminderExpiry, ReminderToken } from "../entities/Game";

// Recordatorios: cuándo se quitan solos y cuáles dejan sin habilidad.

export const EXPIRY_LABEL: Record<ReminderExpiry, string> = {
  dawn: "se quita al amanecer",
  dusk: "se quita al anochecer",
};

/** Siguiente opción al tocar un recordatorio: para siempre → al amanecer → al anochecer → para siempre. */
export const nextExpiry = (until: ReminderExpiry | undefined): ReminderExpiry | undefined =>
  until === undefined ? "dawn" : until === "dawn" ? "dusk" : undefined;

/** Recordatorios genéricos que se pueden poner a cualquiera. */
export const GENERIC_REMINDERS = ["Envenenado", "Borracho", "Protegido", "Muerto esta noche", "Sin habilidad", "Usado"];

/**
 * Cuándo se quita por defecto cada recordatorio, según quién lo pone. Lo que no está aquí se queda
 * hasta que el Narrador lo quite. «dawn»: dura solo esta noche (Monje, muertes para anunciar).
 * «dusk»: dura esta noche y el día siguiente (Envenenador, Marinero, Mayordomo).
 */
const BY_CHARACTER: Record<string, Record<string, ReminderExpiry>> = {
  poisoner: { Envenenado: "dusk" },
  sailor: { Borracho: "dusk" },
  innkeeper: { Borracho: "dusk", "A salvo": "dawn" },
  goon: { Borracho: "dusk" },
  monk: { "A salvo": "dawn" },
  butler: { Señor: "dusk" },
  exorcist: { Elegido: "dawn" },
  lunatic: { Elegido: "dawn" },
  cerenovus: { Loco: "dusk" },
  harpy: { Loco: "dusk" },
  witch: { Maldito: "dusk" },
  undertaker: { "Muerto hoy": "dawn" },
  godfather: { "Muere hoy": "dawn" },
  zombuul: { "Muere hoy": "dawn" },
  barber: { "Corte de pelo": "dawn" },
  flowergirl: { "Demonio ha votado": "dawn", "Demonio no ha votado": "dawn" },
  towncrier: { "Esbirro ha nominado": "dawn", "Esbirro no ha nominado": "dawn" },
  custom: { Envenenado: "dusk", Protegido: "dawn", "Muerto esta noche": "dawn" },
};

export function defaultExpiry(sourceCharacterId: string, label: string): ReminderExpiry | undefined {
  const byLabel = BY_CHARACTER[sourceCharacterId]?.[label];
  if (byLabel) return byLabel;
  // «Muerto» lo ponen el Demonio y quien mata de noche: al amanecer se anuncia y se pone el sudario.
  if (label === "Muerto") return "dawn";
  return undefined;
}

export const makeReminder = (id: string, sourceCharacterId: string, label: string): ReminderToken => {
  const until = defaultExpiry(sourceCharacterId, label);
  return until ? { id, sourceCharacterId, label, until } : { id, sourceCharacterId, label };
};

const DISABLING = /envenen|borrach|sin habilidad|loc[oa]|enloquec/i;

/** Si el recordatorio deja al jugador sin habilidad (hay que darle información falsa). */
export const disablesAbility = (reminder: ReminderToken) => DISABLING.test(reminder.label);

/** Los recordatorios que se quitan al llegar ese momento, y los que quedan. */
export function expireReminders(reminders: ReminderToken[], at: ReminderExpiry) {
  const expired = reminders.filter((r) => r.until === at);
  return { expired, kept: reminders.filter((r) => r.until !== at) };
}
