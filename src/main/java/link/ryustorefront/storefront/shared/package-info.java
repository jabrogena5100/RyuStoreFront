/**
 * Small value types shared by every module (money, ids). Declared an open module so other
 * modules may depend on its internals; it must never depend on them.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package link.ryustorefront.storefront.shared;

import org.springframework.modulith.ApplicationModule;
