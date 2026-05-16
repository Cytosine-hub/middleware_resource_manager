package com.middleware.manager.repository;

import com.middleware.manager.domain.AdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByUsername(String username);
    List<AdminAccount> findAllByOrderByCreatedAtAsc();
    long countByRole(String role);
}
