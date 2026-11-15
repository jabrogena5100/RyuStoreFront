package link.ryustorefront.storefront.catalog.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import link.ryustorefront.storefront.catalog.CategoryView;
import link.ryustorefront.storefront.catalog.PageResult;
import link.ryustorefront.storefront.catalog.ProductDetail;
import link.ryustorefront.storefront.catalog.ProductSort;
import link.ryustorefront.storefront.catalog.ProductSummary;
import link.ryustorefront.storefront.catalog.SearchCriteria;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Catalog reads in plain SQL (the search hot path). Every value reaches Postgres as a bound
 * parameter; only fixed SQL fragments are ever concatenated, and the sort comes from an enum.
 *
 * Shape (chosen from EXPLAIN at 50,000 products, see docs/explain): filtering, sorting and the
 * count use only the products table and its indexes (price comes from the trigger-maintained
 * products.min_price_minor). Only the 24-ish rows of the requested page are then joined to
 * categories and to the per-product currency / in-stock lookup.
 */
@Repository
public class ProductSearchRepository {

    /** A product is listed when it is active and has at least one active variant (min price is not null). */
    private static final String LISTED = "p.active AND p.min_price_minor IS NOT NULL";

    private final JdbcClient jdbc;

    public ProductSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<ProductSummary> search(SearchCriteria c) {
        Map<String, Object> params = new HashMap<>();
        String filters = filters(c, params);

        long total = jdbc.sql("SELECT count(*) FROM products p WHERE " + LISTED + filters)
                .params(params).query(Long.class).single();

        long offset = (long) c.page() * c.pageSize();
        if (total == 0 || offset >= total) {
            return new PageResult<>(List.of(), c.page(), c.pageSize(), total);
        }

        params.put("limit", c.pageSize());
        params.put("offset", offset);
        String relevance = c.sort() == ProductSort.RELEVANCE
                ? ", ts_rank(p.search_vector, websearch_to_tsquery('english', :q)) AS relevance" : "";
        String sql = """
                SELECT t.id, t.slug, t.name, t.brand, t.gender,
                       cat.slug AS category_slug, cat.name AS category_name,
                       t.min_price_minor AS min_price, v.currency, v.in_stock
                FROM (SELECT p.id, p.slug, p.name, p.brand, p.gender, p.category_id, p.min_price_minor, p.created_at%s
                      FROM products p
                      WHERE %s%s
                      ORDER BY %s
                      LIMIT :limit OFFSET :offset) t
                JOIN categories cat ON cat.id = t.category_id
                CROSS JOIN LATERAL (
                    SELECT min(currency) AS currency, bool_or(stock > 0) AS in_stock
                    FROM variants
                    WHERE product_id = t.id AND active
                ) v
                ORDER BY %s""".formatted(relevance, LISTED, filters, orderBy(c, "p"), orderBy(c, "t"));

        List<ProductSummary> items = jdbc.sql(sql)
                .params(params).query(ProductSearchRepository::mapSummary).list();
        return new PageResult<>(items, c.page(), c.pageSize(), total);
    }

    public Optional<ProductDetail> findBySlug(String slug) {
        Optional<ProductDetail> product = jdbc.sql("""
                SELECT p.id, p.slug, p.name, p.brand, p.description, p.gender,
                       cat.slug AS category_slug, cat.name AS category_name
                FROM products p JOIN categories cat ON cat.id = p.category_id
                WHERE p.slug = :slug AND p.active""")
                .param("slug", slug)
                .query((rs, i) -> new ProductDetail(
                        rs.getLong("id"), rs.getString("slug"), rs.getString("name"), rs.getString("brand"),
                        rs.getString("description"), rs.getString("gender"),
                        rs.getString("category_slug"), rs.getString("category_name"), List.of()))
                .optional();
        if (product.isEmpty()) {
            return Optional.empty();
        }

        ProductDetail base = product.get();
        List<ProductDetail.VariantView> variants = jdbc.sql("""
                SELECT sku, size_label, colour, price_minor, currency, stock > 0 AS available
                FROM variants
                WHERE product_id = :id AND active
                ORDER BY colour, id""")
                .param("id", base.id())
                .query((rs, i) -> new ProductDetail.VariantView(
                        rs.getString("sku"), rs.getString("size_label"), rs.getString("colour"),
                        rs.getLong("price_minor"), rs.getString("currency"), rs.getBoolean("available")))
                .list();
        return Optional.of(new ProductDetail(base.id(), base.slug(), base.name(), base.brand(),
                base.description(), base.gender(), base.categorySlug(), base.categoryName(), variants));
    }

    public List<CategoryView> categories() {
        return jdbc.sql("""
                SELECT c.slug, c.name, parent.slug AS parent_slug
                FROM categories c LEFT JOIN categories parent ON parent.id = c.parent_id
                ORDER BY c.name""")
                .query((rs, i) -> new CategoryView(rs.getString("slug"), rs.getString("name"), rs.getString("parent_slug")))
                .list();
    }

    private static String filters(SearchCriteria c, Map<String, Object> params) {
        StringBuilder sql = new StringBuilder();
        if (c.q() != null) {
            sql.append(" AND p.search_vector @@ websearch_to_tsquery('english', :q)");
            params.put("q", c.q());
        }
        List<String> categories = categorySlugs(c.category());
        if (!categories.isEmpty()) {
            // One slug or several (comma-separated, any of them matches). A parent category matches its
            // children too (sneakers includes chunky sneakers).
            // With categoryExact only the named categories match, not their children.
            sql.append(c.categoryExact()
                    ? " AND p.category_id IN (SELECT id FROM categories WHERE slug IN (:categories))"
                    : " AND p.category_id IN (SELECT id FROM categories WHERE slug IN (:categories)"
                            + " OR parent_id IN (SELECT id FROM categories WHERE slug IN (:categories)))");
            params.put("categories", categories);
        }
        if (c.gender() != null) {
            sql.append(" AND (p.gender = :gender OR p.gender = 'unisex')");
            params.put("gender", c.gender());
        }
        if (c.brand() != null) {
            sql.append(" AND p.brand = :brand");
            params.put("brand", c.brand());
        }
        if (c.minPriceMinor() != null) {
            sql.append(" AND p.min_price_minor >= :minPrice");
            params.put("minPrice", c.minPriceMinor());
        }
        if (c.maxPriceMinor() != null) {
            sql.append(" AND p.min_price_minor <= :maxPrice");
            params.put("maxPrice", c.maxPriceMinor());
        }
        if (c.sizeLabel() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM variants sv"
                    + " WHERE sv.product_id = p.id AND sv.active AND sv.stock > 0 AND sv.size_label = :sizeLabel)");
            params.put("sizeLabel", c.sizeLabel());
        }
        if (c.inStockOnly()) {
            sql.append(" AND EXISTS (SELECT 1 FROM variants sv2"
                    + " WHERE sv2.product_id = p.id AND sv2.active AND sv2.stock > 0)");
        }
        return sql.toString();
    }

    /** Splits the category parameter: blank entries are dropped, so "," or "boots,," are harmless. */
    private static List<String> categorySlugs(String category) {
        if (category == null) {
            return List.of();
        }
        return java.util.Arrays.stream(category.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    /** The same ordering is applied inside the page subquery (alias p) and again outside it (alias t). */
    private static String orderBy(SearchCriteria c, String alias) {
        boolean inner = alias.equals("p");
        return switch (c.sort()) {
            case RELEVANCE -> inner
                    ? "ts_rank(p.search_vector, websearch_to_tsquery('english', :q)) DESC, p.id"
                    : "t.relevance DESC, t.id";
            case NEWEST -> alias + ".created_at DESC, " + alias + ".id DESC";
            case PRICE_ASC -> alias + ".min_price_minor ASC, " + alias + ".id";
            case PRICE_DESC -> alias + ".min_price_minor DESC, " + alias + ".id";
        };
    }

    private static ProductSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ProductSummary(
                rs.getLong("id"), rs.getString("slug"), rs.getString("name"), rs.getString("brand"),
                rs.getString("gender"), rs.getString("category_slug"), rs.getString("category_name"),
                rs.getLong("min_price"), rs.getString("currency"), rs.getBoolean("in_stock"));
    }
}
