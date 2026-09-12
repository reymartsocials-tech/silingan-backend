package com.ria.olita.tech.silingan.repository;

import com.ria.olita.tech.silingan.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
	Optional<User> findByKeycloakUserId(String keycloakUserId);

	Optional<User> findByMobileNumber(String mobileNumber);

	/**
	 * Duplicate-account check for email.
	 *
	 * <p>Scoped to live rows so it agrees with the partial unique index
	 * {@code uk_users_email_active}: a soft-deleted account must not block
	 * re-registration with the same address.
	 */
	boolean existsByEmailAndDeletedFalse(String email);

	/** Duplicate-account check for mobile number; mirrors {@code uk_users_mobile_number_active}. */
	boolean existsByMobileNumberAndDeletedFalse(String mobileNumber);


	@Query("""
	           SELECT u.id
	           FROM User u
	           WHERE u.keycloakUserId = ?1
	       """)
	Optional<String> getUserIdByKeycloakUserId(String keycloakUserId);
}
