import type { AppSettings } from "@application/ports/SettingsStore";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { buildContainer, settingsStore, type Container } from "./container";

/** Narrador (puede crear y llevar partidas) o invitado (solo puede unirse como jugador). */
export type Role = "loading" | "admin" | "guest";

interface AppContextValue {
  app: Container;
  role: Role;
  refreshRole(): Promise<void>;
  settings: AppSettings;
  updateSettings(settings: AppSettings): void;
  toast(message: string, kind?: "info" | "error", action?: ToastAction): void;
}

/** Botón dentro del aviso (p. ej. «Deshacer»). El aviso dura más para que dé tiempo a tocarlo. */
export interface ToastAction {
  label: string;
  onClick: () => void;
}

const Ctx = createContext<AppContextValue | null>(null);

interface Toast {
  id: number;
  message: string;
  kind: "info" | "error";
  action?: ToastAction;
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState(() => settingsStore.load());
  const app = useMemo(() => buildContainer(settings), [settings]);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [role, setRole] = useState<Role>("loading");

  const refreshRole = useCallback(async () => {
    try {
      setRole((await app.admin.check()) ? "admin" : "guest");
    } catch {
      setRole("guest");
    }
  }, [app]);

  useEffect(() => {
    void refreshRole();
  }, [refreshRole]);

  const updateSettings = useCallback((next: AppSettings) => {
    settingsStore.save(next);
    setSettings(next);
  }, []);

  const toast = useCallback((message: string, kind: "info" | "error" = "info", action?: ToastAction) => {
    const id = Date.now() + Math.random();
    // Un aviso con botón sustituye al anterior con botón: solo se puede deshacer lo último.
    setToasts((t) => [...t.filter((x) => !(action && x.action)), { id, message, kind, action }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), kind === "error" || action ? 6000 : 3000);
  }, []);

  const value = useMemo(
    () => ({ app, role, refreshRole, settings, updateSettings, toast }),
    [app, role, refreshRole, settings, updateSettings, toast],
  );

  return (
    <Ctx.Provider value={value}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast--${t.kind} ${t.action ? "toast--action" : ""}`}>
            <span>{t.message}</span>
            {t.action && (
              <button
                type="button"
                className="btn btn--small btn--gold"
                onClick={() => {
                  setToasts((list) => list.filter((x) => x.id !== t.id));
                  t.action!.onClick();
                }}
              >
                {t.action.label}
              </button>
            )}
          </div>
        ))}
      </div>
    </Ctx.Provider>
  );
}

export function useApp() {
  const value = useContext(Ctx);
  if (!value) throw new Error("useApp fuera de AppProvider");
  return value;
}

/** Mensaje legible de cualquier error para enseñarlo en un aviso. */
export const errorMessage = (e: unknown) => (e instanceof Error ? e.message : String(e));
