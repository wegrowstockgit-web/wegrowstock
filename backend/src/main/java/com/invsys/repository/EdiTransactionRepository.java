package com.invsys.repository;

import com.invsys.domain.EdiTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EdiTransactionRepository extends JpaRepository<EdiTransaction, UUID> {
}
