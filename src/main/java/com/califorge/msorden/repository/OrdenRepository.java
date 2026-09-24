package com.califorge.msorden.repository;

import com.califorge.msorden.model.Orden;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, UUID> {

    Page<Orden> findByUsuarioSubOrderByFechaCreacionDesc(String usuarioSub, Pageable pageable);

    Optional<Orden> findByIdAndUsuarioSub(UUID id, String usuarioSub);

    Optional<Orden> findByIdempotencyKey(String idempotencyKey);
}
