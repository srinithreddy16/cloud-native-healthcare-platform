package org.srinith.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.srinith.authservice.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
}
