package com.medchart.ehr.repository;

import com.medchart.ehr.domain.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("select case when count(u) > 0 then true else false end "
            + "from User u join u.roles r where r = :role and u.enabled = true")
    boolean existsEnabledUserWithRole(@Param("role") User.Role role);
}