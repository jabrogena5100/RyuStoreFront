package link.ryustorefront.storefront.orders;

import java.util.UUID;
import link.ryustorefront.storefront.auth.CustomerPrincipal;
import link.ryustorefront.storefront.shared.ApiException;
import link.ryustorefront.storefront.shared.StructuredErrors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads one of the signed-in customer's orders (the security rules answer a guest with 401 first). Someone else's order and
 * an unknown id give the same 404, as in the pay endpoint.
 */
@RestController
@StructuredErrors
@RequestMapping("/api/orders")
class OrdersController {

    private final Orders orders;

    OrdersController(Orders orders) {
        this.orders = orders;
    }

    @GetMapping("/{id}")
    OrderDetail get(@AuthenticationPrincipal CustomerPrincipal customer, @PathVariable UUID id) {
        return orders.findForCustomer(id, customer.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "order_not_found", "No such order."));
    }
}
