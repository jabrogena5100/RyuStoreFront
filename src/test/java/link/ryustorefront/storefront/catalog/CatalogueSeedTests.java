package link.ryustorefront.storefront.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.catalog.internal.CatalogueFile;
import link.ryustorefront.storefront.catalog.internal.DemoDataSeeder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The curated catalogue seed (V11 product_images, DemoDataSeeder.seedCatalogue) and the image fields of the catalogue
 * API, through real HTTP and a real Postgres. Expected values come from the fixture and from SQL, not from the code path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CatalogueSeedTests {

    @Autowired
    Environment env;
    @Autowired
    DemoDataSeeder seeder;
    @Autowired
    JdbcClient jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private static final CatalogueFile FIXTURE = new CatalogueFile(1, List.of(
            new CatalogueFile.Product("seedtest-court-sneaker", "Seedtest Court Sneaker", "Plain White Co.", "sneakers", "unisex",
                    List.of("white", "black"), 145, "A court shoe with nothing on it but the stitching.",
                    List.of(new CatalogueFile.Image("products/seedtest-court-sneaker-1", 2000, 1333, "White leather court sneaker, side view"),
                            new CatalogueFile.Image("products/seedtest-court-sneaker-2", 2000, 2500, "The same sneaker from above"))),
            new CatalogueFile.Product("seedtest-chelsea-boot", "Seedtest Chelsea Boot", "Ostrava Atelier", "boots", "women",
                    List.of("brown"), 260, "Suede Chelsea boot with elastic sides.", List.of())));

    @BeforeEach
    @AfterEach
    void clean() {
        seeder.clear();
    }

    @Test
    void everyColourGetsTheWholeSizeWindowAndImagesKeepTheirOrder() {
        seeder.seedCatalogue(FIXTURE);

        long sneaker = id("seedtest-court-sneaker");
        Map<String, Long> perColour = jdbc.sql("SELECT colour, count(*) FROM variants WHERE product_id = :id GROUP BY colour")
                .param("id", sneaker).query((rs, i) -> Map.entry(rs.getString(1), rs.getLong(2))).list()
                .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(perColour.keySet()).containsExactlyInAnyOrder("white", "black");
        assertThat(perColour.get("white")).isEqualTo(perColour.get("black")).isBetween(6L, 9L);
        assertThat(jdbc.sql("SELECT DISTINCT price_minor FROM variants WHERE product_id = :id").param("id", sneaker)
                .query(Long.class).list()).containsExactly(14500L);

        assertThat(jdbc.sql("SELECT image_key FROM product_images WHERE product_id = :id ORDER BY position")
                .param("id", sneaker).query(String.class).list())
                .containsExactly("products/seedtest-court-sneaker-1", "products/seedtest-court-sneaker-2");
        assertThat(jdbc.sql("SELECT count(*) FROM product_images WHERE product_id = :id").param("id", id("seedtest-chelsea-boot"))
                .query(Long.class).single()).isZero();
    }

    @Test
    void theApiReturnsTheFirstImageOnSummariesAndAllImagesOnTheDetail() throws Exception {
        seeder.seedCatalogue(FIXTURE);
        String base = env.getRequiredProperty("storefront.images.base-url").replaceAll("/+$", "");

        String list = ok("/api/products?sort=newest");
        // File order is newest first.
        assertThat(JsonPath.<List<String>>read(list, "$.items[*].slug")).containsExactly("seedtest-court-sneaker", "seedtest-chelsea-boot");
        assertThat(JsonPath.<String>read(list, "$.items[0].image.base")).isEqualTo(base + "/products/seedtest-court-sneaker-1");
        assertThat(JsonPath.<Integer>read(list, "$.items[0].image.width")).isEqualTo(2000);
        assertThat(JsonPath.<Integer>read(list, "$.items[0].image.height")).isEqualTo(1333);
        assertThat(JsonPath.<String>read(list, "$.items[0].image.alt")).isEqualTo("White leather court sneaker, side view");
        assertThat(JsonPath.<Object>read(list, "$.items[1].image")).isNull();

        String detail = ok("/api/products/seedtest-court-sneaker");
        assertThat(JsonPath.<List<String>>read(detail, "$.images[*].base"))
                .containsExactly(base + "/products/seedtest-court-sneaker-1", base + "/products/seedtest-court-sneaker-2");
        assertThat(JsonPath.<List<Object>>read(ok("/api/products/seedtest-chelsea-boot"), "$.images")).isEmpty();
    }

    @Test
    void seedingTwiceGivesTheSameSkusAndStock() {
        seeder.seedCatalogue(FIXTURE);
        List<String> first = jdbc.sql("SELECT sku || ':' || stock FROM variants ORDER BY sku").query(String.class).list();
        seeder.clear();
        seeder.seedCatalogue(FIXTURE);
        List<String> second = jdbc.sql("SELECT sku || ':' || stock FROM variants ORDER BY sku").query(String.class).list();
        assertThat(second).isNotEmpty().isEqualTo(first);
    }

    @Test
    void aBadEntryInsertsNothing() {
        CatalogueFile unknownCategory = new CatalogueFile(1, List.of(new CatalogueFile.Product("seedtest-x", "X", "Halden", "spaceboots",
                "men", List.of("black"), 100, "x", List.of())));
        assertThatThrownBy(() -> seeder.seedCatalogue(unknownCategory)).hasMessageContaining("spaceboots");
        assertThat(seeder.productCount()).isZero();

        CatalogueFile badKey = new CatalogueFile(1, List.of(new CatalogueFile.Product("seedtest-y", "Y", "Halden", "boots",
                "men", List.of("black"), 100, "y", List.of(new CatalogueFile.Image("Products/Has Spaces.jpg", 10, 10, "y")))));
        assertThatThrownBy(() -> seeder.seedCatalogue(badKey)).hasMessageContaining("product_images_key_format");
        assertThat(seeder.productCount()).as("products and images are written in one transaction").isZero();
    }

    private long id(String slug) {
        return jdbc.sql("SELECT id FROM products WHERE slug = :slug").param("slug", slug).query(Long.class).single();
    }

    private String ok(String pathAndQuery) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(
                "http://localhost:" + env.getRequiredProperty("local.server.port", Integer.class) + pathAndQuery)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).as("%s -> %s", pathAndQuery, r.body()).isEqualTo(200);
        return r.body();
    }
}
