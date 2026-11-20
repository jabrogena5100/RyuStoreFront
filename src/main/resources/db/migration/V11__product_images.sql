-- Product photos (phase 4 of the finishing plan). The files live in object storage (SeaweedFS locally, S3 behind
-- CloudFront in AWS); a row holds the object key prefix and the original's dimensions. The served files are
-- <base-url>/<image_key>-<width>.<avif|webp> for the widths the frontend asks for (scripts/images/variants.mjs).
-- Licence, author and source for every key are in content/catalogue/manifest.json, which the credits page lists.
-- Images belong to their product: deleting a product deletes its image rows (products are normally deactivated, not deleted).

CREATE TABLE product_images (
    id         bigint   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id bigint   NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    position   smallint NOT NULL,
    image_key  text     NOT NULL,
    width      integer  NOT NULL,
    height     integer  NOT NULL,
    alt        text     NOT NULL,
    CONSTRAINT product_images_position_uniq UNIQUE (product_id, position),
    CONSTRAINT product_images_position_nonneg CHECK (position >= 0),
    CONSTRAINT product_images_key_format CHECK (image_key ~ '^[a-z0-9]+(-[a-z0-9]+)*(/[a-z0-9]+(-[a-z0-9]+)*)*$'),
    CONSTRAINT product_images_size_positive CHECK (width > 0 AND height > 0),
    CONSTRAINT product_images_alt_nonblank CHECK (length(btrim(alt)) > 0)
);
-- The position-0 lookup for each listed product uses the unique index (product_id, position).
