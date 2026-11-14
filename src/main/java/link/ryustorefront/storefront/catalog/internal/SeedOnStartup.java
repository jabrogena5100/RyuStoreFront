package link.ryustorefront.storefront.catalog.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Run with the `seed` profile to fill an empty database. STOREFRONT_SEED_PRODUCTS sets the count (default 1000). */
@Component
@Profile("seed")
class SeedOnStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedOnStartup.class);

    private final DemoDataSeeder seeder;
    private final int count;

    SeedOnStartup(DemoDataSeeder seeder, @Value("${storefront.seed.products:1000}") int count) {
        this.seeder = seeder;
        this.count = count;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = seeder.productCount();
        if (existing > 0) {
            log.info("Seed skipped: {} products already present", existing);
            return;
        }
        long started = System.currentTimeMillis();
        int inserted = seeder.seed(count);
        log.info("Seeded {} products in {} ms", inserted, System.currentTimeMillis() - started);
    }
}
