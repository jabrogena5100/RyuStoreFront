import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { AuthProvider } from "@/components/AuthProvider";
import { CartDrawerProvider } from "@/components/CartDrawer";
import { CartProvider } from "@/components/CartProvider";
import { ToastProvider } from "@/components/ui/Toast";
import "./globals.css";

// Self-hosted: next/font downloads Geist at build time, subsets it to Latin and serves it from this origin (no request to
// Google at run time). The `geist` npm package was tried first and shipped the full fonts: 138 KB against 30 KB before
// (docs/metrics.md F8).
const sans = Geist({ subsets: ["latin"], variable: "--font-geist-sans", display: "swap" });
const mono = Geist_Mono({ subsets: ["latin"], variable: "--font-geist-mono", display: "swap" });

export const metadata: Metadata = {
  title: { default: "RyuStoreFront", template: "%s · RyuStoreFront" },
  description: "Sneakers, runners, boots and evening shoes. Pick an occasion, tap your size, done.",
};

export const viewport: Viewport = {
  themeColor: "#141414",
  colorScheme: "dark",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${sans.variable} ${mono.variable}`}>
      <body>
        <AuthProvider>
          <CartProvider>
            <ToastProvider>
              <CartDrawerProvider>{children}</CartDrawerProvider>
            </ToastProvider>
          </CartProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
