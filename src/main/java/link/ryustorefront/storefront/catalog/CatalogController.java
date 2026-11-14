package link.ryustorefront.storefront.catalog;

import java.util.List;
import link.ryustorefront.storefront.catalog.internal.ProductSearchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class CatalogController {

    static final int MAX_PAGE_SIZE = 48;
    static final int MAX_PAGE = 10_000;
    static final int MAX_QUERY_LENGTH = 100;

    private final ProductSearchRepository products;

    CatalogController(ProductSearchRepository products) {
        this.products = products;
    }

    @GetMapping("/products")
    PageResult<ProductSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) String size,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int pageSize) {

        if (page < 0 || page > MAX_PAGE) {
            throw badRequest("page must be between 0 and " + MAX_PAGE);
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw badRequest("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0)) {
            throw badRequest("minPrice and maxPrice must not be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw badRequest("minPrice must not exceed maxPrice");
        }
        String query = blankToNull(q);
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            throw badRequest("q must be at most " + MAX_QUERY_LENGTH + " characters");
        }

        ProductSort requested;
        try {
            requested = ProductSort.parse(sort);
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }
        // Relevance only means something with a search term; default to it when searching.
        ProductSort effective = requested != null ? requested : (query != null ? ProductSort.RELEVANCE : ProductSort.NEWEST);
        if (effective == ProductSort.RELEVANCE && query == null) {
            effective = ProductSort.NEWEST;
        }

        return products.search(new SearchCriteria(
                query, blankToNull(category), blankToNull(gender), blankToNull(brand),
                minPrice, maxPrice, blankToNull(size), inStock, effective, page, pageSize));
    }

    @GetMapping("/products/{slug}")
    ProductDetail detail(@PathVariable String slug) {
        return products.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such product"));
    }

    @GetMapping("/categories")
    List<CategoryView> categories() {
        return products.categories();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
