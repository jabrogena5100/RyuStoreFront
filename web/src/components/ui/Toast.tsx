"use client";

import { Check } from "lucide-react";
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";

interface ToastItem {
  id: number;
  message: string;
}

const ToastContext = createContext<{ show: (message: string) => void } | null>(null);

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside ToastProvider");
  return ctx;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const next = useRef(1);

  const show = useCallback((message: string) => {
    const id = next.current++;
    setItems((list) => [...list.slice(-2), { id, message }]);
    window.setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), 3200);
  }, []);
  const value = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 px-4 pb-[max(1rem,env(safe-area-inset-bottom))]"
      >
        {items.map((t) => (
          <div key={t.id} className="toast pointer-events-auto flex items-center gap-3 rounded-md border border-line bg-surface-3 px-4 py-3 text-small text-fg-strong shadow-overlay">
            <Check aria-hidden="true" className="size-4 text-success" />
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
