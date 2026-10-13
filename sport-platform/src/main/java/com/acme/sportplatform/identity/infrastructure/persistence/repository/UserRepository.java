package com.acme.sportplatform.identity.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByEmailIgnoreCase(String email);

    @Query(value = """
            select u.* from users u left join profiles p on p.user_id = u.id
            where (:query = '' or u.email ilike :pattern escape '\\' or p.first_name ilike :pattern escape '\\'
                   or p.last_name ilike :pattern escape '\\')
              and (:role = '' or exists (select 1 from user_role_assignments a join roles r on r.id = a.role_id
                                         where a.user_id = u.id and r.code = :role))
            order by u.created_at desc, u.id
            """, countQuery = """
            select count(*) from users u left join profiles p on p.user_id = u.id
            where (:query = '' or u.email ilike :pattern escape '\\' or p.first_name ilike :pattern escape '\\'
                   or p.last_name ilike :pattern escape '\\')
              and (:role = '' or exists (select 1 from user_role_assignments a join roles r on r.id = a.role_id
                                         where a.user_id = u.id and r.code = :role))
            """, nativeQuery = true)
    Page<UserEntity> search(String query, String pattern, String role,
            Pageable pageable);
    boolean existsByEmail(String email);
}
