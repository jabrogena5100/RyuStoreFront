"use client";

import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { ConfirmationBody, OrderPageBody, PayBody, type PayState } from "@/components/OrderViews";
import { ButtonLink, Button } from "@/components/ui/Button";
import { Container } from "@/components/ui/Container";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/StatePanel";
import { ApiClientError, api } from "@/lib/api-client";
import type { ApiOrder } from "@/lib/orders";

type Load = { phase: "loading" } | { phase: "ready"; order: ApiOrder } | { phase: "notfound" | "signedout" | "error" };

/** Reads GET /api/orders/{id} in the browser (the session cookie goes with it). `pollMs` re-reads while the order is pending. */
function useOrder(id: string, pollMs?: number) {
  const [load, setLoad] = useState<Load>({ phase: "loading" });
  const stopped = useRef(false);

  const reload = useCallback(async () => {
    try {
      const order = await api<ApiOrder>(`/orders/${id}`);
      setLoad({ phase: "ready", order });
      return order;
    } catch (e) {
      if (e instanceof ApiClientError && e.status === 404) setLoad({ phase: "notfound" });
      else if (e instanceof ApiClientError && e.status === 401) setLoad({ phase: "signedout" });
      else setLoad((cur) => (cur.phase === "ready" ? cur : { phase: "error" })); // a failed poll keeps what is on screen
      return null;
    }
  }, [id]);

  useEffect(() => {
    stopped.current = false;
    let timer: number | undefined;
    const tick = async () => {
      const o = await reload();
      if (!stopped.current && pollMs && (!o || o.status === "pending")) timer = window.setTimeout(tick, pollMs);
    };
    tick();
    return () => {
      stopped.current = true;
      window.clearTimeout(timer);
    };
  }, [reload, pollMs]);

  return { load, reload };
}

function Waiting() {
  return (
    <Container className="py-8">
      <div role="status" aria-busy="true" aria-label="Loading your order" className="flex flex-col gap-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-48 w-full rounded-lg" />
        <Skeleton className="h-32 w-full rounded-lg" />
      </div>
    </Container>
  );
}

/** The states every order screen shares; returns null when the order is ready. */
function Problem({ load, retry }: { load: Load; retry: () => void }) {
  const path = usePathname();
  if (load.phase === "ready") return null;
  if (load.phase === "loading") return <Waiting />;
  return (
    <Container className="py-16">
      {load.phase === "notfound" ? (
        <EmptyState title="We could not find that order" action={<ButtonLink href="/orders">Your orders</ButtonLink>}>
          It may belong to another account, or the link may be wrong.
        </EmptyState>
      ) : load.phase === "signedout" ? (
        <EmptyState title="Sign in to see this order" action={<ButtonLink href={`/sign-in?next=${encodeURIComponent(path)}`}>Sign in</ButtonLink>}>
          Orders are saved to your account.
        </EmptyState>
      ) : (
        <ErrorState title="We could not load this order" action={<Button onClick={retry}>Try again</Button>}>
          Your order is safe. Check your connection and try again.
        </ErrorState>
      )}
    </Container>
  );
}

export function LiveOrderPage({ id }: { id: string }) {
  const { load, reload } = useOrder(id);
  return load.phase === "ready" ? <OrderPageBody order={load.order} /> : <Problem load={load} retry={reload} />;
}

/** Polls every 3 seconds while the order is pending: the webhook, not this page, is what marks it paid. */
export function LiveConfirmation({ id }: { id: string }) {
  const { load, reload } = useOrder(id, 3000);
  return load.phase === "ready" ? <ConfirmationBody order={load.order} onCheckAgain={() => void reload()} /> : <Problem load={load} retry={reload} />;
}

export function LivePay({ id }: { id: string }) {
  const router = useRouter();
  const { load, reload } = useOrder(id);
  const [state, setState] = useState<PayState>("idle");
  const order = load.phase === "ready" ? load.order : null;

  // Only a pending order can be paid; anything else belongs on the order page.
  useEffect(() => {
    if (order && order.status !== "pending") router.replace(order.status === "paid" ? `/orders/${id}/confirmation` : `/orders/${id}`);
  }, [order, id, router]);

  async function pay() {
    setState("starting");
    try {
      const r = await api<{ url: string }>(`/orders/${id}/pay`, { method: "POST" });
      window.location.assign(r.url); // stays "starting" while the browser leaves
    } catch (e) {
      if (e instanceof ApiClientError && (e.code === "order_expired" || e.code === "payment_expired")) setState("expired");
      else if (e instanceof ApiClientError && e.code === "order_not_payable") router.replace(`/orders/${id}`);
      else if (e instanceof ApiClientError && e.code === "payments_unavailable") setState("unavailable");
      else setState("provider");
    }
  }

  if (!order || order.status !== "pending") return <Problem load={order ? { phase: "loading" } : load} retry={reload} />;
  const lapsed = order.expiresAt !== null && new Date(order.expiresAt).getTime() < Date.now();
  return <PayBody order={order} state={lapsed ? "expired" : state} onPay={pay} />;
}
