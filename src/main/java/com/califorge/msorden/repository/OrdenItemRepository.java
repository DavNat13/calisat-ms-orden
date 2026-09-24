package com.califorge.msorden.repository;

import com.califorge.msorden.model.OrdenItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrdenItemRepository extends JpaRepository<OrdenItem, UUID> {

    List<OrdenItem> findByOrdenId(UUID ordenId);
}
