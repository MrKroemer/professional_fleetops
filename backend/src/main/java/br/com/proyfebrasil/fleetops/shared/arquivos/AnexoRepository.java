package br.com.proyfebrasil.fleetops.shared.arquivos;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso aos metadados de anexo. */
public interface AnexoRepository extends JpaRepository<Anexo, Long> {
}
