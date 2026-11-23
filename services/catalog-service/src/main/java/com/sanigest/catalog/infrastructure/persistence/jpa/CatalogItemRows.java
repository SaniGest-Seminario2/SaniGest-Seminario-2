package com.sanigest.catalog.infrastructure.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CatalogItemRows extends JpaRepository<CatalogItemRow, UUID> {}
