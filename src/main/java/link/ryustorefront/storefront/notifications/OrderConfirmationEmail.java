package link.ryustorefront.storefront.notifications;

import java.util.Currency;
import java.util.StringJoiner;
import link.ryustorefront.storefront.orders.OrderSummary;
import link.ryustorefront.storefront.orders.ShippingAddress;
import link.ryustorefront.storefront.shared.Money;

/**
 * Builds the "payment received" email for an order. Pure: no I/O, no Spring, so it is tested directly.
 *
 * <p>Everything that came from a person or from the catalogue (names, address lines, product names) is
 * HTML-escaped in the HTML part, so a product called {@code <script>} cannot inject markup. The text part
 * is plain text and is not escaped.
 */
public final class OrderConfirmationEmail {

    private OrderConfirmationEmail() {
    }

    /** The short reference shown to the customer: the first eight characters of the order id, uppercased. */
    public static String orderNumber(OrderSummary order) {
        return order.id().toString().substring(0, 8).toUpperCase();
    }

    public static EmailContent build(OrderSummary order) {
        String number = orderNumber(order);
        Currency currency = Currency.getInstance(order.currency());
        ShippingAddress a = order.shippingAddress();
        String greetingName = a != null && a.name() != null && !a.name().isBlank() ? a.name() : "there";

        return new EmailContent(
                "Your order " + number + " is confirmed",
                text(order, number, currency, a, greetingName),
                html(order, number, currency, a, greetingName));
    }

    private static String money(long minor, Currency currency) {
        return new Money(minor, currency).toString();
    }

    private static String text(OrderSummary order, String number, Currency currency, ShippingAddress a, String name) {
        StringBuilder out = new StringBuilder();
        out.append("Hi ").append(name).append(",\n\n");
        out.append("Thank you. We have received your payment for order ").append(number).append(".\n\n");
        for (OrderSummary.Line line : order.items()) {
            out.append(line.quantity()).append(" x ").append(line.productName())
                    .append(" (size ").append(line.sizeLabel()).append(", ").append(line.colour()).append(")")
                    .append("  ").append(money(line.lineTotalMinor(), currency)).append('\n');
        }
        out.append('\n');
        out.append("Subtotal: ").append(money(order.subtotalMinor(), currency)).append('\n');
        out.append("Shipping: ").append(money(order.shippingMinor(), currency)).append('\n');
        out.append("Total:    ").append(money(order.totalMinor(), currency)).append("\n\n");
        if (a != null) {
            out.append("Shipping to:\n");
            addressLines(a).forEach(l -> out.append(l).append('\n'));
            out.append('\n');
        }
        out.append("Keep this email for your records.\n");
        return out.toString();
    }

    private static String html(OrderSummary order, String number, Currency currency, ShippingAddress a, String name) {
        StringBuilder out = new StringBuilder();
        out.append("<!doctype html><html><body style=\"font-family:sans-serif;color:#222\">");
        out.append("<p>Hi ").append(Html.escape(name)).append(",</p>");
        out.append("<p>Thank you. We have received your payment for order <strong>")
                .append(Html.escape(number)).append("</strong>.</p>");
        out.append("<table style=\"border-collapse:collapse\">");
        for (OrderSummary.Line line : order.items()) {
            out.append("<tr><td style=\"padding:4px 12px 4px 0\">").append(line.quantity()).append(" &times; ")
                    .append(Html.escape(line.productName())).append(" (size ").append(Html.escape(line.sizeLabel()))
                    .append(", ").append(Html.escape(line.colour())).append(")</td>")
                    .append("<td style=\"padding:4px 0;text-align:right\">")
                    .append(Html.escape(money(line.lineTotalMinor(), currency))).append("</td></tr>");
        }
        out.append(totalRow("Subtotal", money(order.subtotalMinor(), currency), false));
        out.append(totalRow("Shipping", money(order.shippingMinor(), currency), false));
        out.append(totalRow("Total", money(order.totalMinor(), currency), true));
        out.append("</table>");
        if (a != null) {
            out.append("<p><strong>Shipping to</strong><br>");
            StringJoiner lines = new StringJoiner("<br>");
            addressLines(a).forEach(l -> lines.add(Html.escape(l)));
            out.append(lines).append("</p>");
        }
        out.append("<p>Keep this email for your records.</p></body></html>");
        return out.toString();
    }

    private static String totalRow(String label, String amount, boolean bold) {
        String open = bold ? "<strong>" : "";
        String close = bold ? "</strong>" : "";
        return "<tr><td style=\"padding:4px 12px 4px 0\">" + open + label + close + "</td>"
                + "<td style=\"padding:4px 0;text-align:right\">" + open + Html.escape(amount) + close + "</td></tr>";
    }

    private static java.util.List<String> addressLines(ShippingAddress a) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        addIfPresent(lines, a.name());
        addIfPresent(lines, a.line1());
        addIfPresent(lines, a.line2());
        String cityLine = String.join(" ", java.util.stream.Stream.of(
                a.city() == null || a.city().isBlank() ? null : a.city() + ",", a.region(), a.postalCode())
                .filter(s -> s != null && !s.isBlank()).toList());
        addIfPresent(lines, cityLine);
        addIfPresent(lines, a.country());
        return lines;
    }

    private static void addIfPresent(java.util.List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }
}
