package com.huylq.it6027.backend.repository;

import com.huylq.it6027.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

  @Query("""
      SELECT u FROM User u
      JOIN FETCH u.role r
      LEFT JOIN FETCH r.permissions
      WHERE u.username = :username
      """)
  Optional<User> findByUsernameWithRole(@Param("username") String username);
}
