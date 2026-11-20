package link.ryustorefront.storefront.catalog.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deterministic demo catalogue: footwear from invented brands (no real trademarks). The same
 * seed always produces the same products, so tests and EXPLAIN runs are repeatable.
 * Prices are plausible list prices in whole-dollar steps; copy is generated from per-category
 * phrase banks, not lorem ipsum.
 *
 * Rows are written in batches of {@value #CHUNK} products, one transaction per batch, so a large
 * seed is fast and an interrupted one keeps the batches already committed.
 */
@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final long RANDOM_SEED = 42L;
    private static final int CHUNK = 500;

    private record Kind(String category, int weight, List<String> genders, List<String> types,
                        int loDollars, int hiDollars, List<String> brands, List<String> materials,
                        List<String> colours, List<String> details) {
    }

    /** A generated product waiting to be written: its row values plus its variants without a product id. */
    private record Pending(String slug, Object[] productRow, List<Object[]> variantRows) {
    }

    private static final List<String[]> CATEGORIES = List.of(
            new String[] {"sneakers", "Sneakers", null},
            new String[] {"chunky-sneakers", "Chunky sneakers", "sneakers"},
            new String[] {"boots", "Boots", null},
            new String[] {"heels", "Heels", null},
            new String[] {"loafers", "Loafers", null},
            new String[] {"running", "Running", null},
            new String[] {"sandals", "Sandals", null},
            new String[] {"oxfords", "Oxfords and derbies", null});

    private static final List<Kind> KINDS = List.of(
            new Kind("sneakers", 18, List.of("unisex", "men", "women"),
                    List.of("Court Sneaker", "Low Sneaker", "Tennis Shoe", "Leather Trainer", "Minimal Sneaker"),
                    95, 185, List.of("Plain White Co.", "Halden"),
                    List.of("full-grain white leather", "pebbled calf leather", "brushed suede"),
                    List.of("white", "off-white", "black", "stone", "sage", "navy"),
                    List.of("Wipes clean with a damp cloth.", "Stitched cupsole, not glued.",
                            "Unlined heel for a quiet break-in.", "Replaceable insole.")),
            new Kind("chunky-sneakers", 12, List.of("unisex", "women", "men"),
                    List.of("Platform Sneaker", "Dad Sneaker", "Runner Trainer", "Chunky Trainer"),
                    120, 210, List.of("Slab & Sole"),
                    List.of("mesh and suede overlays", "leather with a layered foam midsole", "nubuck and ripstop"),
                    List.of("white", "cream", "sand", "grey", "black"),
                    List.of("A 45 mm stacked sole adds height without weight.", "Padded tongue and collar.",
                            "Wide toe box with a sculpted heel counter.", "Reflective heel tab.")),
            new Kind("boots", 18, List.of("men", "women", "unisex"),
                    List.of("Chelsea Boot", "Lace-Up Boot", "Combat Boot", "Ankle Boot", "Western Boot", "Hiking Boot"),
                    160, 480, List.of("Halden", "Tarn", "Ostrava Atelier"),
                    List.of("oiled leather", "waxed suede", "polished calfskin"),
                    List.of("black", "tan", "chestnut", "oxblood", "olive"),
                    List.of("Goodyear-welted so it can be resoled.", "Leather-lined with a cushioned footbed.",
                            "Pull tabs front and back.", "Rubber lug sole for wet pavement.")),
            new Kind("heels", 12, List.of("women"),
                    List.of("Stiletto Pump", "Slingback", "Block Heel Sandal", "Kitten Heel", "Platform Pump"),
                    525, 1295, List.of("Maison Verre", "Noir Meridian"),
                    List.of("Italian nappa leather", "patent calfskin", "satin with a leather lining"),
                    List.of("black", "nude", "ivory", "burgundy", "gold"),
                    List.of("Hand-lacquered leather outsole.", "Padded insole with a non-slip heel grip.",
                            "Made on a narrow last; size up for a wider foot.", "Comes with a dust bag and spare heel tips.")),
            new Kind("loafers", 10, List.of("men", "women"),
                    List.of("Penny Loafer", "Horsebit Loafer", "Tassel Loafer", "Driving Moccasin"),
                    180, 650, List.of("Ostrava Atelier", "Maison Verre"),
                    List.of("burnished calfskin", "suede", "grained leather"),
                    List.of("black", "burgundy", "tan", "brown", "navy"),
                    List.of("Blake-stitched with a slim leather sole.", "Unlined for a softer fit.",
                            "Rubber half-sole fitted at the factory.", "Cut low over the instep.")),
            // Road running only. Racers (carbon plate, race-day copy) and trainers are separate kinds so a description
            // never mixes them, and there is no trail shoe: the shop has no trail range.
            new Kind("running", 4, List.of("men", "women", "unisex"),
                    List.of("Carbon Racer", "Marathon Racer"),
                    220, 300, List.of("Kestrel Run", "Tarn"),
                    List.of("engineered knit", "lightweight woven upper"),
                    List.of("black", "white", "volt", "coral", "slate"),
                    List.of("Carbon-fibre plate for a stiff, springy toe-off.", "Road racing shoe: smooth, fast and light.",
                            "Rockered, high-stack foam midsole tuned for race day.", "6 mm heel-to-toe drop.")),
            new Kind("running", 8, List.of("men", "women", "unisex"),
                    List.of("Road Trainer", "Tempo Shoe"),
                    120, 200, List.of("Kestrel Run", "Tarn"),
                    List.of("engineered knit", "recycled mesh", "lightweight woven upper"),
                    List.of("black", "white", "volt", "coral", "slate"),
                    List.of("Road shoe with a durable rubber outsole.", "8 mm heel-to-toe drop.",
                            "Breathable mesh upper for daily miles.", "Foam midsole tuned for tempo runs and easy days.")),
            new Kind("sandals", 8, List.of("women", "men", "unisex"),
                    List.of("Slide", "Strappy Sandal", "Sport Sandal", "Mule"),
                    65, 220, List.of("Slab & Sole", "Maison Verre"),
                    List.of("vegetable-tanned leather", "moulded EVA", "woven leather"),
                    List.of("black", "tan", "white", "sage", "clay"),
                    List.of("Contoured footbed.", "Adjustable ankle strap.",
                            "Dries quickly after a swim.", "Cork-latex footbed that moulds to the foot.")),
            new Kind("oxfords", 10, List.of("men", "women"),
                    List.of("Cap-Toe Oxford", "Derby", "Brogue", "Monk Strap"),
                    220, 580, List.of("Ostrava Atelier", "Noir Meridian"),
                    List.of("box calf", "hand-burnished calfskin", "grain leather"),
                    List.of("black", "oxblood", "brown", "tan", "navy"),
                    List.of("Leather sole, resoleable.", "Closed lacing for a dressier line.",
                            "Padded collar and lined in calf leather.", "Fitted with a cork-filled footbed.")));

    private static final List<String> MODEL_WORDS = List.of(
            "Aurel", "Brisa", "Calder", "Dorian", "Elan", "Fenwick", "Gavra", "Hollis", "Ione", "Jasper",
            "Kiran", "Lucan", "Marais", "Nerea", "Orsen", "Perrin", "Quill", "Rhea", "Soren", "Talia",
            "Ulric", "Vesper", "Wren", "Xavi", "Yara", "Zeno", "Alder", "Bexley", "Corin", "Dale",
            "Eero", "Faro", "Garnet", "Harlan", "Isla", "Juno", "Kestrel", "Lorne", "Mika", "Nico",
            "Olwen", "Piper", "Quincy", "Rowan", "Sable", "Tobin", "Umber", "Vale", "Wilder", "Yves",
            "Arden", "Bram", "Cleo", "Dara", "Emory", "Finn", "Greer", "Hale", "Idris", "Jory",
            "Kai", "Leith", "Marlo", "Nell", "Odin", "Pia", "Quest", "Reed", "Sela", "Tarn",
            "Uri", "Vida", "Wynn", "Xan", "Yael", "Zora", "Ansel", "Briar", "Cass", "Dov");

    private static final String INSERT_PRODUCT = """
            INSERT INTO products (slug, name, brand, description, category_id, gender, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INSERT_VARIANT = """
            INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock)
            VALUES (?, ?, ?, ?, ?, ?)""";

    private final JdbcClient jdbc;
    private final JdbcTemplate template;
    private final TransactionTemplate transactions;

    public DemoDataSeeder(JdbcClient jdbc, JdbcTemplate template, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.template = template;
        this.transactions = transactions;
    }

    public long productCount() {
        return jdbc.sql("SELECT count(*) FROM products").query(Long.class).single();
    }

    /** Inserts {@code productCount} products with variants; about 1% are inactive. Returns how many were inserted. */
    public int seed(int productCount) {
        Map<String, Long> categoryIds = transactions.execute(status -> insertCategories());
        Set<String> usedSlugs = new HashSet<>(jdbc.sql("SELECT slug FROM products").query(String.class).list());
        long serial = productCount() + 1;
        Random random = new Random(RANDOM_SEED);
        int totalWeight = KINDS.stream().mapToInt(Kind::weight).sum();
        List<Pending> chunk = new ArrayList<>(CHUNK);

        for (int i = 0; i < productCount; i++, serial++) {
            Kind kind = pickKind(random, totalWeight);
            String gender = pick(random, kind.genders());
            String brand = pick(random, kind.brands());
            String type = pick(random, kind.types());
            String name = pick(random, MODEL_WORDS) + " " + type;
            String slug = slugify(brand + " " + name);
            for (int n = 2; usedSlugs.contains(slug); n++) {
                name = name.replaceAll(" Mk \\d+$", "") + " Mk " + n;
                slug = slugify(brand + " " + name);
            }
            usedSlugs.add(slug);

            String description = type + " in " + pick(random, kind.materials()) + ". "
                    + pick(random, kind.details()) + " " + pick(random, kind.details());
            boolean active = random.nextInt(100) >= 1;
            Instant createdAt = Instant.now().minus(random.nextInt(180 * 24), ChronoUnit.HOURS);
            Object[] productRow = {slug, name, brand, description, categoryIds.get(kind.category()),
                    gender, active, Timestamp.from(createdAt)};

            long priceDollars = (kind.loDollars() + random.nextInt(kind.hiDollars() - kind.loDollars() + 1)) / 5 * 5;
            long priceMinor = Math.max(priceDollars, 5) * 100;
            List<String> sizes = sizeWindow(random, gender);
            List<Object[]> variants = new ArrayList<>();
            for (String colour : pickDistinct(random, kind.colours(), 1 + random.nextInt(3))) {
                for (String size : sizes) {
                    int roll = random.nextInt(100);
                    int stock = roll < 12 ? 0 : roll < 30 ? 1 + random.nextInt(3) : 4 + random.nextInt(22);
                    String sku = initials(brand) + "-" + String.format("%05d", serial) + "-" + colourCode(colour)
                            + "-" + size.replace('.', '-');
                    variants.add(new Object[] {sku, size, colour, priceMinor, stock});
                }
            }
            chunk.add(new Pending(slug, productRow, variants));

            if (chunk.size() == CHUNK || i == productCount - 1) {
                List<Pending> toWrite = List.copyOf(chunk);
                transactions.executeWithoutResult(status -> writeChunk(toWrite));
                chunk.clear();
                if ((i + 1) % 5_000 == 0 || i == productCount - 1) {
                    log.info("Seeded {} of {} products", i + 1, productCount);
                }
            }
        }
        return productCount;
    }

    /**
     * Inserts the curated catalogue: one product per entry, in file order (the first entry is the newest), with every
     * size in the gender's window for each listed colour, deterministic stock per product, and its photos in order.
     * Returns how many products were inserted.
     */
    public int seedCatalogue(CatalogueFile file) {
        Map<String, Long> categoryIds = transactions.execute(status -> insertCategories());
        long serial = productCount() + 1;
        Instant now = Instant.now();
        List<Pending> pending = new ArrayList<>();
        List<CatalogueFile.Product> products = file.products();
        for (int i = 0; i < products.size(); i++, serial++) {
            CatalogueFile.Product p = products.get(i);
            Long categoryId = categoryIds.get(p.category());
            if (categoryId == null) {
                throw new IllegalArgumentException("Unknown category '" + p.category() + "' for " + p.slug());
            }
            if (p.colours() == null || p.colours().isEmpty()) {
                throw new IllegalArgumentException("No colours for " + p.slug());
            }
            Object[] productRow = {p.slug(), p.name(), p.brand(), p.description(), categoryId, p.gender(), true,
                    Timestamp.from(now.minus(i, ChronoUnit.HOURS))};
            Random random = new Random(p.slug().hashCode());
            List<String> sizes = sizeWindow(random, p.gender());
            List<Object[]> variants = new ArrayList<>();
            for (String colour : p.colours()) {
                for (String size : sizes) {
                    int roll = random.nextInt(100);
                    int stock = roll < 12 ? 0 : roll < 30 ? 1 + random.nextInt(3) : 4 + random.nextInt(22);
                    String sku = initials(p.brand()) + "-" + String.format("%05d", serial) + "-" + colourCode(colour)
                            + "-" + size.replace('.', '-');
                    variants.add(new Object[] {sku, size, colour, p.priceDollars() * 100L, stock});
                }
            }
            pending.add(new Pending(p.slug(), productRow, variants));
        }
        transactions.executeWithoutResult(status -> {
            writeChunk(pending);
            List<Object[]> imageRows = new ArrayList<>();
            for (CatalogueFile.Product p : products) {
                long productId = jdbc.sql("SELECT id FROM products WHERE slug = :slug").param("slug", p.slug())
                        .query(Long.class).single();
                List<CatalogueFile.Image> images = p.images() == null ? List.of() : p.images();
                for (int pos = 0; pos < images.size(); pos++) {
                    CatalogueFile.Image img = images.get(pos);
                    imageRows.add(new Object[] {productId, pos, img.key(), img.width(), img.height(), img.alt()});
                }
            }
            template.batchUpdate("""
                    INSERT INTO product_images (product_id, position, image_key, width, height, alt)
                    VALUES (?, ?, ?, ?, ?, ?)""", imageRows);
        });
        log.info("Seeded {} catalogue products", products.size());
        return products.size();
    }

    /** Removes all catalogue rows. Test use only: fails if orders or carts reference variants. */
    @Transactional
    public void clear() {
        jdbc.sql("DELETE FROM product_images").update();
        jdbc.sql("DELETE FROM variants").update();
        jdbc.sql("DELETE FROM products").update();
        jdbc.sql("DELETE FROM categories WHERE parent_id IS NOT NULL").update();
        jdbc.sql("DELETE FROM categories").update();
    }

    private void writeChunk(List<Pending> pending) {
        template.batchUpdate(INSERT_PRODUCT, pending.stream().map(Pending::productRow).toList());

        Map<String, Long> idsBySlug = new HashMap<>();
        jdbc.sql("SELECT id, slug FROM products WHERE slug IN (:slugs)")
                .param("slugs", pending.stream().map(Pending::slug).toList())
                .query((rs, i) -> {
                    idsBySlug.put(rs.getString("slug"), rs.getLong("id"));
                    return null;
                }).list();

        List<Object[]> variantRows = new ArrayList<>();
        for (Pending p : pending) {
            long productId = idsBySlug.get(p.slug());
            for (Object[] v : p.variantRows()) {
                variantRows.add(new Object[] {productId, v[0], v[1], v[2], v[3], v[4]});
            }
        }
        template.batchUpdate(INSERT_VARIANT, variantRows);
    }

    private Map<String, Long> insertCategories() {
        Map<String, Long> ids = new HashMap<>();
        for (String[] c : CATEGORIES) {
            Long parent = c[2] == null ? null : ids.get(c[2]);
            jdbc.sql("INSERT INTO categories (slug, name, parent_id) VALUES (:slug, :name, :parent) ON CONFLICT DO NOTHING")
                    .param("slug", c[0]).param("name", c[1]).param("parent", parent, java.sql.Types.BIGINT).update();
            ids.put(c[0], jdbc.sql("SELECT id FROM categories WHERE slug = :slug").param("slug", c[0])
                    .query(Long.class).single());
        }
        return ids;
    }

    private static Kind pickKind(Random random, int totalWeight) {
        int roll = random.nextInt(totalWeight);
        for (Kind kind : KINDS) {
            roll -= kind.weight();
            if (roll < 0) {
                return kind;
            }
        }
        return KINDS.get(0);
    }

    /** A contiguous run of 6 to 9 sizes: women US 5 to 11, everyone else US 7 to 13, in half sizes. */
    private static List<String> sizeWindow(Random random, String gender) {
        int lowHalfSteps = gender.equals("women") ? 10 : 14;
        int highHalfSteps = gender.equals("women") ? 22 : 26;
        int length = 6 + random.nextInt(4);
        int start = lowHalfSteps + random.nextInt(highHalfSteps - lowHalfSteps - length + 2);
        List<String> sizes = new ArrayList<>();
        for (int h = start; h < start + length && h <= highHalfSteps; h++) {
            sizes.add(h % 2 == 0 ? String.valueOf(h / 2) : (h / 2) + ".5");
        }
        return sizes;
    }

    private static <T> T pick(Random random, List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    private static List<String> pickDistinct(Random random, List<String> list, int count) {
        List<String> copy = new ArrayList<>(list);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(count, copy.size()); i++) {
            out.add(copy.remove(random.nextInt(copy.size())));
        }
        return out;
    }

    private static String slugify(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static String initials(String brand) {
        StringBuilder sb = new StringBuilder();
        for (String word : brand.split("\\s+")) {
            if (!word.isEmpty() && Character.isLetter(word.charAt(0))) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return sb.toString();
    }

    private static String colourCode(String colour) {
        String letters = colour.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        return letters.substring(0, Math.min(3, letters.length()));
    }
}
