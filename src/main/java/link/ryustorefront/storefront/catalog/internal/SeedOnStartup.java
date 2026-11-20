package link.ryustorefront.storefront.catalog.internal;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Run with the `seed` profile to fill an EMPTY database. storefront.seed.source picks what: "catalogue" (the default)
 * loads the curated, photographed catalogue from classpath:catalogue/catalogue.json and falls back to "generated" when
 * that file is not packaged; "generated" makes STOREFRONT_SEED_PRODUCTS random products without photos (default 1000).
 */
@Component
@Profile("seed")
class SeedOnStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedOnStartup.class);
    static final String CATALOGUE = "catalogue/catalogue.json";

    private final DemoDataSeeder seeder;
    private final int count;
    private final String source;

    SeedOnStartup(DemoDataSeeder seeder, @Value("${storefront.seed.products:1000}") int count,
                  @Value("${storefront.seed.source:catalogue}") String source) {
        this.seeder = seeder;
        this.count = count;
        this.source = source;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        long existing = seeder.productCount();
        if (existing > 0) {
            log.info("Seed skipped: {} products already present", existing);
            return;
        }
        long started = System.currentTimeMillis();
        ClassPathResource catalogue = new ClassPathResource(CATALOGUE);
        if (source.equals("catalogue") && catalogue.exists()) {
            try (Reader in = new InputStreamReader(catalogue.getInputStream(), StandardCharsets.UTF_8)) {
                int inserted = seeder.seedCatalogue(new Gson().fromJson(in, CatalogueFile.class));
                log.info("Seeded {} catalogue products in {} ms", inserted, System.currentTimeMillis() - started);
            }
            return;
        }
        if (source.equals("catalogue")) {
            log.info("No {} on the classpath: seeding generated products instead", CATALOGUE);
        }
        int inserted = seeder.seed(count);
        log.info("Seeded {} products in {} ms", inserted, System.currentTimeMillis() - started);
    }
}
