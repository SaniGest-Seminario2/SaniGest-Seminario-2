ALTER TABLE catalog.suppliers DROP CONSTRAINT suppliers_supply_type_check;
ALTER TABLE catalog.suppliers ADD CONSTRAINT suppliers_supply_type_check
    CHECK (supply_type IN ('PRODUCT', 'SERVICE', 'BOTH', 'REGULAR'));
