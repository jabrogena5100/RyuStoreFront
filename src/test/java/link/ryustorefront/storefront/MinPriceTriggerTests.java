package link.ryustorefront.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * products.min_price_minor is maintained by triggers on variants (V7). These tests pin the
 * behaviour, including that stock updates (the reservation hot path) never rewrite the product row.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MinPriceTriggerTests {

    @Autowired
    JdbcClient jdbc;

    @Test
    void aProductWithNoVariantsHasNoMinPrice() {
        assertThat(minPrice(product())).isNull();
    }

    @Test
    void firstActiveVariantSetsTheMinPrice() {
        long product = product();
        variant(product, "A", "40", 20_000, 5, true);

        assertThat(minPrice(product)).isEqualTo(20_000L);
    }

    @Test
    void aCheaperVariantLowersItAndADearerOneDoesNot() {
        long product = product();
        variant(product, "A", "40", 20_000, 5, true);
        variant(product, "B", "41", 15_000, 5, true);
        variant(product, "C", "42", 30_000, 5, true);

        assertThat(minPrice(product)).isEqualTo(15_000L);
    }

    @Test
    void anInactiveVariantIsIgnoredOnInsert() {
        long product = product();
        variant(product, "A", "40", 5_000, 5, false);

        assertThat(minPrice(product)).isNull();
    }

    @Test
    void changingTheCheapestPriceRecomputesTheMinimum() {
        long product = product();
        long cheapest = variant(product, "A", "40", 15_000, 5, true);
        variant(product, "B", "41", 20_000, 5, true);

        jdbc.sql("UPDATE variants SET price_minor = 25000 WHERE id = ?").param(cheapest).update();

        assertThat(minPrice(product)).isEqualTo(20_000L);
    }

    @Test
    void deactivatingTheCheapestVariantRaisesTheMinimum() {
        long product = product();
        long cheapest = variant(product, "A", "40", 15_000, 5, true);
        variant(product, "B", "41", 20_000, 5, true);

        jdbc.sql("UPDATE variants SET active = false WHERE id = ?").param(cheapest).update();

        assertThat(minPrice(product)).isEqualTo(20_000L);
    }

    @Test
    void deletingOrDeactivatingTheLastVariantClearsIt() {
        long product = product();
        long only = variant(product, "A", "40", 15_000, 5, true);

        jdbc.sql("DELETE FROM variants WHERE id = ?").param(only).update();

        assertThat(minPrice(product)).isNull();
    }

    @Test
    void stockUpdatesDoNotRewriteTheProductRow() {
        long product = product();
        long variant = variant(product, "A", "40", 15_000, 5, true);
        String before = rowVersion(product);

        jdbc.sql("UPDATE variants SET stock = stock - 1 WHERE id = ?").param(variant).update();

        assertThat(rowVersion(product)).as("location of the product row").isEqualTo(before);
    }

    @Test
    void aPriceChangeDoesRewriteTheProductRow() {
        long product = product();
        long variant = variant(product, "A", "40", 15_000, 5, true);
        String before = rowVersion(product);

        jdbc.sql("UPDATE variants SET price_minor = 14000 WHERE id = ?").param(variant).update();

        assertThat(minPrice(product)).isEqualTo(14_000L);
        assertThat(rowVersion(product)).isNotEqualTo(before);
    }

    // ---- helpers ----------------------------------------------------------------------------

    private long product() {
        long category = jdbc.sql("INSERT INTO categories (slug, name) VALUES ('boots', 'Boots') RETURNING id")
                .query(Long.class).single();
        return jdbc.sql("""
                INSERT INTO products (slug, name, brand, category_id, gender)
                VALUES ('test-shoe', 'Test shoe', 'Test brand', ?, 'unisex') RETURNING id""")
                .param(category).query(Long.class).single();
    }

    private long variant(long productId, String sku, String size, long price, int stock, boolean active) {
        return jdbc.sql("""
                INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock, active)
                VALUES (?, ?, ?, 'black', ?, ?, ?) RETURNING id""")
                .params(productId, sku, size, price, stock, active).query(Long.class).single();
    }

    private Long minPrice(long productId) {
        return jdbc.sql("SELECT min_price_minor FROM products WHERE id = ?").param(productId)
                .query((rs, i) -> {
                    long value = rs.getLong(1);
                    return rs.wasNull() ? null : value;
                }).list().getFirst();
    }

    /**
     * ctid (the row's physical location) changes whenever the row is rewritten, even inside one
     * transaction, so it shows whether a statement touched the product row.
     */
    private String rowVersion(long productId) {
        return jdbc.sql("SELECT ctid::text FROM products WHERE id = ?").param(productId)
                .query(String.class).single();
    }
}
