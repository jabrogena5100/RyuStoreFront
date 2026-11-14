package link.ryustorefront.storefront;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** "Modular monolith" is checked by a test: this fails if a module reaches into another's internals or a cycle appears. */
class ModularityTests {

    @Test
    void moduleBoundariesAreRespected() {
        ApplicationModules.of(StorefrontApplication.class).verify();
    }
}
