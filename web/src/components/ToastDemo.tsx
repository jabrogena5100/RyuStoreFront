"use client";

import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";

export function ToastDemo() {
  const { show } = useToast();
  return (
    <Button variant="secondary" onClick={() => show("Added Aurel Court Sneaker, size 9")}>
      Show a toast
    </Button>
  );
}
