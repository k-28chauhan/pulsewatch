package com.karaninder.pulseWatch.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	/** The email passed in must already be lower-cased (AuthService normalises it). */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
