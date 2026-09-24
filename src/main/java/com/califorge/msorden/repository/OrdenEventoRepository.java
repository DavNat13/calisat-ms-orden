package com.califorge.msorden.repository;

import com.califorge.msorden.model.OrdenEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrdenEventoRepository extends JpaRepository<OrdenEvento, Long> {

    List<OrdenEvento> findByOrdenIdOrderByFechaEventoDesc(UUID ordenId);
}
