package link.ryustorefront.storefront.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import link.ryustorefront.storefront.orders.OrderSummary;
import link.ryustorefront.storefront.orders.ShippingAddress;
import org.junit.jupiter.api.Test;

/** The email builder is pure, so it is tested directly, including the HTML-injection cases. */
class OrderConfirmationEmailTests {

    private static final UUID ID = UUID.fromString("1234abcd-0000-4000-8000-000000000001");

    private static OrderSummary order(String currency, ShippingAddress address, List<OrderSummary.Line> lines,
                                      long subtotal, long shipping) {
        return new OrderSummary(ID, "paid", "buyer@example.com", currency, subtotal, shipping, subtotal + shipping,
                address, null, Instant.parse("2026-11-14T10:00:00Z"), lines);
    }

    private static final ShippingAddress ADDRESS =
            new ShippingAddress("Ada Buyer", "1 Main St", "Apt 4", "Portland", "OR", "97201", "US");

    private static final OrderSummary.Line BOOT =
            new OrderSummary.Line("SKU-1", "Chelsea Boot", "42", "black", 12_990, 2, 25_980);

    @Test
    void theSubjectCarriesTheShortOrderNumber() {
        EmailContent email = OrderConfirmationEmail.build(order("USD", ADDRESS, List.of(BOOT), 25_980, 800));

        assertThat(OrderConfirmationEmail.orderNumber(order("USD", ADDRESS, List.of(BOOT), 25_980, 800))).isEqualTo("1234ABCD");
        assertThat(email.subject()).isEqualTo("Your order 1234ABCD is confirmed");
    }

    @Test
    void theTextBodyListsItemsTotalsAndTheAddressInOrder() {
        EmailContent email = OrderConfirmationEmail.build(order("USD", ADDRESS, List.of(BOOT), 25_980, 800));

        assertThat(email.text()).contains("Hi Ada Buyer,")
                .contains("order 1234ABCD")
                .contains("2 x Chelsea Boot (size 42, black)  259.80 USD")
                .contains("Subtotal: 259.80 USD")
                .contains("Shipping: 8.00 USD")
                .contains("Total:    267.80 USD")
                .contains("Shipping to:\nAda Buyer\n1 Main St\nApt 4\nPortland, OR 97201\nUS\n");
        assertThat(email.text()).as("plain text has no markup").doesNotContain("<");
    }

    @Test
    void anAbsentSecondAddressLineLeavesNoBlankLine() {
        ShippingAddress noLine2 = new ShippingAddress("Ada Buyer", "1 Main St", null, "Portland", "OR", "97201", "US");

        EmailContent email = OrderConfirmationEmail.build(order("USD", noLine2, List.of(BOOT), 25_980, 800));

        assertThat(email.text()).contains("1 Main St\nPortland, OR 97201\nUS\n");
        assertThat(email.html()).contains("1 Main St<br>Portland, OR 97201");
    }

    @Test
    void theHtmlBodyEscapesProductNamesAndAddressesSoNothingCanInjectMarkup() {
        OrderSummary.Line nasty = new OrderSummary.Line("SKU-2", "<script>alert(1)</script> Boot & \"Co\"", "4<2", "red'", 1_000, 1, 1_000);
        ShippingAddress evil = new ShippingAddress("Tom <img src=x onerror=alert(1)>", "1 <b>Main</b> St", null,
                "Port & Land", "OR", "97201", "US");

        EmailContent email = OrderConfirmationEmail.build(order("USD", evil, List.of(nasty), 1_000, 800));

        assertThat(email.html()).doesNotContain("<script>").doesNotContain("<img").doesNotContain("<b>Main</b>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt; Boot &amp; &quot;Co&quot;")
                .contains("size 4&lt;2, red&#39;")
                .contains("Tom &lt;img src=x onerror=alert(1)&gt;")
                .contains("Port &amp; Land");
        assertThat(email.text()).as("the text part is plain text, shown literally")
                .contains("<script>alert(1)</script> Boot & \"Co\"");
    }

    @Test
    void unicodeSurvivesInBothParts() {
        OrderSummary.Line line = new OrderSummary.Line("SKU-3", "Müller Stiefel 日本語 🙂", "42", "schwarz", 1_000, 1, 1_000);
        ShippingAddress address = new ShippingAddress("Zoë Ångström", "Straße 5", null, "München", "BY", "80331", "DE");

        EmailContent email = OrderConfirmationEmail.build(order("USD", address, List.of(line), 1_000, 800));

        assertThat(email.text()).contains("Müller Stiefel 日本語 🙂").contains("Zoë Ångström").contains("München, BY 80331");
        assertThat(email.html()).contains("Müller Stiefel 日本語 🙂").contains("Zoë Ångström");
    }

    @Test
    void currenciesWithoutMinorUnitsAreFormattedWithoutDecimals() {
        OrderSummary.Line line = new OrderSummary.Line("SKU-4", "Geta", "26", "natural", 5_000, 1, 5_000);

        EmailContent email = OrderConfirmationEmail.build(order("JPY", ADDRESS, List.of(line), 5_000, 800));

        assertThat(email.text()).contains("5000 JPY").contains("Total:    5800 JPY");
    }

    @Test
    void aMissingAddressOrNameStillProducesAnEmail() {
        EmailContent noAddress = OrderConfirmationEmail.build(order("USD", null, List.of(BOOT), 25_980, 800));
        ShippingAddress noName = new ShippingAddress("  ", "1 Main St", null, "Portland", "OR", "97201", "US");
        EmailContent blankName = OrderConfirmationEmail.build(order("USD", noName, List.of(BOOT), 25_980, 800));

        assertThat(noAddress.text()).startsWith("Hi there,").doesNotContain("Shipping to:");
        assertThat(noAddress.html()).doesNotContain("Shipping to");
        assertThat(blankName.text()).startsWith("Hi there,");
    }

    @Test
    void htmlEscapesTheFiveSpecialCharactersAndToleratesNull() {
        assertThat(Html.escape("<a href=\"x\">Tom & 'Jerry'</a>"))
                .isEqualTo("&lt;a href=&quot;x&quot;&gt;Tom &amp; &#39;Jerry&#39;&lt;/a&gt;");
        assertThat(Html.escape(null)).isEmpty();
        assertThat(Html.escape("plain")).isEqualTo("plain");
    }
}
