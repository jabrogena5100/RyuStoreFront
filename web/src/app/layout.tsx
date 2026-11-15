import type { Metadata, Viewport } from "next";
import { Instrument_Sans } from "next/font/google";
import { CartProvider } from "@/components/CartProvider";
import { Footer } from "@/components/Footer";
import { Header } from "@/components/Header";
import { ToastProvider } from "@/components/ui/Toast";
import "./globals.css";

const sans = Instrument_Sans({ subsets: ["latin"], variable: "--font-instrument-sans", display: "swap" });

export const metadata: Metadata = {
  title: { default: "Storefront", template: "%s · Storefront" },
  description: "Sneakers, runners, boots and evening shoes. Pick an occasion, tap your size, done.",
};

export const viewport: Viewport = {
  themeColor: "#0b0a08",
  colorScheme: "dark",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={sans.variable}>
      <body>
        <CartProvider>
          <ToastProvider>
            <Header />
            <main id="main">{children}</main>
            <Footer />
          </ToastProvider>
        </CartProvider>
      </body>
    </html>
  );
}
