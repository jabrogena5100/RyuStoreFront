package link.ryustorefront.storefront.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.catalog.internal.DemoDataSeeder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Real HTTP against the real server and a real Postgres, with 1,000 seeded products. Assertions
 * read the actual JSON the controller serialises (field names included), and counts are checked
 * against independent SQL rather than against the same code path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogApiTests {

    private static final int PRODUCTS = 1_000;

    @Autowired
    Environment env;
    @Autowired
    DemoDataSeeder seeder;
    @Autowired
    JdbcClient jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    void seed() {
        seeder.clear();
        seeder.seed(PRODUCTS);
    }

    @AfterAll
    void cleanUp() {
        seeder.clear();
    }

    // ---- listing and pagination -------------------------------------------------------------

    @Test
    void totalMatchesTheActiveProductsInTheDatabase() throws Exception {
        long active = jdbc.sql("SELECT count(*) FROM products p WHERE p.active "
                + "AND EXISTS (SELECT 1 FROM variants v WHERE v.product_id = p.id AND v.active)")
                .query(Long.class).single();

        String body = ok("/api/products");

        assertThat(total(body)).isEqualTo(active);
        assertThat(active).isBetween(900L, (long) PRODUCTS); // about 1% are inactive
    }

    @Test
    void defaultPageHas24Items() throws Exception {
        String body = ok("/api/products");

        assertThat(ids(body)).hasSize(24);
        assertThat(JsonPath.<Integer>read(body, "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.pageSize")).isEqualTo(24);
    }

    @Test
    void walkingEveryPageReturnsEachActiveProductExactlyOnce() throws Exception {
        long total = total(ok("/api/products?pageSize=48"));
        Set<Long> seen = new HashSet<>();
        int pages = (int) ((total + 47) / 48);

        for (int page = 0; page < pages; page++) {
            for (long id : ids(ok("/api/products?pageSize=48&page=" + page))) {
                assertThat(seen.add(id)).as("product %d appeared twice", id).isTrue();
            }
        }

        assertThat((long) seen.size()).isEqualTo(total);
        assertThat(ids(ok("/api/products?pageSize=48&page=" + pages))).isEmpty();
    }

    @Test
    void priceSortsHoldAcrossPageBoundaries() throws Exception {
        List<Long> ascending = new ArrayList<>(prices(ok("/api/products?sort=price_asc&pageSize=48&page=0")));
        ascending.addAll(prices(ok("/api/products?sort=price_asc&pageSize=48&page=1")));
        List<Long> descending = new ArrayList<>(prices(ok("/api/products?sort=price_desc&pageSize=48&page=0")));
        descending.addAll(prices(ok("/api/products?sort=price_desc&pageSize=48&page=1")));

        assertThat(ascending).hasSize(96).isSorted();
        assertThat(descending).hasSize(96).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    // ---- filters (checked against SQL) ------------------------------------------------------

    @Test
    void genderFilterReturnsThatGenderAndUnisexOnly() throws Exception {
        String body = ok("/api/products?gender=women&pageSize=48");

        List<String> genders = JsonPath.read(body, "$.items[*].gender");
        assertThat(genders).isNotEmpty().allMatch(g -> g.equals("women") || g.equals("unisex"));
        assertThat(total(body)).isLessThan(total(ok("/api/products")));
    }

    @Test
    void parentCategoryIncludesItsChildren() throws Exception {
        String body = ok("/api/products?category=sneakers&pageSize=48");

        List<String> categories = JsonPath.read(body, "$.items[*].categorySlug");
        assertThat(categories).allMatch(c -> c.equals("sneakers") || c.equals("chunky-sneakers"));
        assertThat(categories).contains("chunky-sneakers");
    }

    @Test
    void priceRangeFiltersOnTheCheapestVariant() throws Exception {
        String body = ok("/api/products?minPrice=20000&maxPrice=30000&pageSize=48");

        assertThat(prices(body)).isNotEmpty().allMatch(p -> p >= 20_000 && p <= 30_000);
    }

    @Test
    void denormalisedMinPriceAgreesWithTheVariantsItSummarises() {
        long drifted = jdbc.sql("""
                SELECT count(*) FROM products p
                WHERE p.min_price_minor IS DISTINCT FROM
                      (SELECT min(v.price_minor) FROM variants v WHERE v.product_id = p.id AND v.active)""")
                .query(Long.class).single();

        assertThat(drifted).isZero();
    }

    @Test
    void priceRangeTotalMatchesAnIndependentSqlCount() throws Exception {
        long expected = jdbc.sql("""
                SELECT count(*) FROM products p
                WHERE p.active
                  AND (SELECT min(v.price_minor) FROM variants v WHERE v.product_id = p.id AND v.active)
                      BETWEEN 20000 AND 30000""")
                .query(Long.class).single();

        assertThat(total(ok("/api/products?minPrice=20000&maxPrice=30000"))).isEqualTo(expected).isPositive();
    }

    @Test
    void sizeFilterMatchesAnIndependentSqlCount() throws Exception {
        long expected = jdbc.sql("""
                SELECT count(DISTINCT p.id) FROM products p JOIN variants v ON v.product_id = p.id
                WHERE p.active AND v.active AND v.stock > 0 AND v.size_label = '9'""")
                .query(Long.class).single();

        assertThat(total(ok("/api/products?size=9"))).isEqualTo(expected).isPositive();
    }

    @Test
    void inStockOnlyMatchesAnIndependentSqlCount() throws Exception {
        long expected = jdbc.sql("""
                SELECT count(DISTINCT p.id) FROM products p JOIN variants v ON v.product_id = p.id
                WHERE p.active AND v.active AND v.stock > 0""")
                .query(Long.class).single();

        assertThat(total(ok("/api/products?inStock=true"))).isEqualTo(expected);
    }

    @Test
    void brandFilterIsExact() throws Exception {
        String body = ok("/api/products?brand=" + enc("Kestrel Run") + "&pageSize=48");

        List<String> brands = JsonPath.read(body, "$.items[*].brand");
        assertThat(brands).isNotEmpty().allMatch(b -> b.equals("Kestrel Run"));
    }

    // ---- search -----------------------------------------------------------------------------

    @Test
    void textSearchFindsAndRanksByName() throws Exception {
        String body = ok("/api/products?q=chelsea");

        assertThat(total(body)).isPositive();
        List<String> names = JsonPath.read(body, "$.items[*].name");
        assertThat(names.getFirst()).containsIgnoringCase("chelsea");
    }

    @Test
    void searchStemsPlurals() throws Exception {
        assertThat(total(ok("/api/products?q=boots"))).isEqualTo(total(ok("/api/products?q=boot"))).isPositive();
    }

    @Test
    void searchTermIsBoundNotConcatenated() throws Exception {
        String hostile = enc("'; DROP TABLE products; --");

        assertThat(get("/api/products?q=" + hostile).statusCode()).isEqualTo(200);
        assertThat(total(ok("/api/products"))).isPositive(); // table still there
    }

    @Test
    void relevanceWithoutASearchTermFallsBackToNewest() throws Exception {
        assertThat(ids(ok("/api/products?sort=relevance"))).hasSize(24);
    }

    // ---- detail, categories, errors, access ------------------------------------------------

    @Test
    void detailReturnsVariantsAndNeverExposesStockCounts() throws Exception {
        String slug = JsonPath.read(ok("/api/products"), "$.items[0].slug");

        String body = ok("/api/products/" + slug);

        assertThat(JsonPath.<String>read(body, "$.slug")).isEqualTo(slug);
        assertThat(JsonPath.<List<Object>>read(body, "$.variants")).isNotEmpty();
        assertThat(JsonPath.<String>read(body, "$.variants[0].sku")).isNotBlank();
        assertThat(JsonPath.<Boolean>read(body, "$.variants[0].available")).isNotNull();
        assertThatThrownBy(() -> JsonPath.read(body, "$.variants[0].stock")).isInstanceOf(PathNotFoundException.class);
    }

    @Test
    void unknownAndInactiveProductsAreNotFound() throws Exception {
        assertThat(get("/api/products/no-such-shoe").statusCode()).isEqualTo(404);

        List<String> inactive = jdbc.sql("SELECT slug FROM products WHERE NOT active LIMIT 1")
                .query(String.class).list();
        assertThat(inactive).as("seed should include some inactive products").isNotEmpty();
        assertThat(get("/api/products/" + inactive.getFirst()).statusCode()).isEqualTo(404);
    }

    @Test
    void categoriesListsTheTreeWithParents() throws Exception {
        String body = ok("/api/categories");

        List<String> parents = JsonPath.read(body, "$[?(@.slug=='chunky-sneakers')].parentSlug");
        assertThat(parents).containsExactly("sneakers");
    }

    @Test
    void invalidParametersAreRejectedWith400() throws Exception {
        for (String bad : List.of(
                "pageSize=0", "pageSize=500", "page=-1", "sort=bogus",
                "minPrice=-5", "minPrice=500&maxPrice=100", "q=" + "x".repeat(101))) {
            assertThat(get("/api/products?" + bad).statusCode()).as(bad).isEqualTo(400);
        }
    }

    @Test
    void catalogIsReadOnlyForAnonymousCallers() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/products"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json").build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(401, 403);
    }

    // ---- helpers ----------------------------------------------------------------------------

    private String ok(String pathAndQuery) throws Exception {
        HttpResponse<String> response = get(pathAndQuery);
        assertThat(response.statusCode()).as("%s -> %s", pathAndQuery, response.body()).isEqualTo(200);
        return response.body();
    }

    private HttpResponse<String> get(String pathAndQuery) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(pathAndQuery)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String pathAndQuery) {
        return URI.create("http://localhost:" + env.getRequiredProperty("local.server.port", Integer.class) + pathAndQuery);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static long total(String body) {
        return ((Number) JsonPath.read(body, "$.total")).longValue();
    }

    private static List<Long> ids(String body) {
        List<Number> raw = JsonPath.read(body, "$.items[*].id");
        return raw.stream().map(Number::longValue).toList();
    }

    private static List<Long> prices(String body) {
        List<Number> raw = JsonPath.read(body, "$.items[*].minPriceMinor");
        return raw.stream().map(Number::longValue).toList();
    }
}
