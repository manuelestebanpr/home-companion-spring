CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE TABLE items(id uuid PRIMARY KEY, name varchar(80) NOT NULL, slug varchar(100) NOT NULL UNIQUE,
 kind varchar(8) NOT NULL CHECK(kind IN ('COUNT','MASS','VOLUME')), category varchar(80) NOT NULL DEFAULT 'General');
CREATE TABLE brands(id uuid PRIMARY KEY, name varchar(80) NOT NULL, slug varchar(100) NOT NULL UNIQUE);
CREATE TABLE attributes(id uuid PRIMARY KEY, name varchar(80) NOT NULL, slug varchar(100) NOT NULL UNIQUE);
CREATE TABLE products(id uuid PRIMARY KEY, item_id uuid REFERENCES items NOT NULL, brand_id uuid REFERENCES brands,
 variant_key varchar(400) NOT NULL, package_qty numeric(12,3) NOT NULL CHECK(package_qty>0),
 unit varchar(4) NOT NULL CHECK(unit IN ('unit','g','kg','ml','l')), product_key varchar(700) NOT NULL UNIQUE,
 UNIQUE NULLS NOT DISTINCT(item_id,brand_id,variant_key,package_qty,unit));
CREATE TABLE product_attributes(product_id uuid REFERENCES products NOT NULL, attribute_id uuid REFERENCES attributes NOT NULL, PRIMARY KEY(product_id,attribute_id));
CREATE TABLE stock(product_id uuid PRIMARY KEY REFERENCES products, packages numeric(12,3) NOT NULL DEFAULT 0 CHECK(packages>=0), version bigint NOT NULL DEFAULT 0, last_purchased_at timestamptz);
CREATE TABLE desired_items(item_id uuid PRIMARY KEY REFERENCES items, target_qty numeric(12,3) NOT NULL CHECK(target_qty>0), unit varchar(4) NOT NULL, preferred_brand_id uuid REFERENCES brands);
CREATE TABLE idempotency_requests(scope varchar(80) NOT NULL, request_id varchar(100) NOT NULL, payload text NOT NULL, result text NOT NULL, expires_at timestamptz NOT NULL, PRIMARY KEY(scope,request_id));
