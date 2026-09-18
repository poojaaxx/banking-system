package com.bankingdemo.billpay;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillerRepository extends JpaRepository<Biller, Long> {
    List<Biller> findByActiveTrue();
    Optional<Biller> findByIdAndActiveTrue(Long id);
}
