package com.medchart.ehr.repository;

import com.medchart.ehr.domain.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where :role member of u.roles")
    List<User> lockUsersWithRole(@Param("role") User.Role role);

    @Query("select count(u) from User u where :role member of u.roles and u.enabled = true "
         + "and u.accountNonLocked = true and u.accountNonExpired = true and u.credentialsNonExpired = true")
    long countUsableUsersWithRole(@Param("role") User.Role role);
}