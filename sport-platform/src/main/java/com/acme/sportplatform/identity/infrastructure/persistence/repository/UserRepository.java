package com.acme.sportplatform.identity.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.projection.UserSearchRow;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(UUID id);

    Optional<UserEntity> findByEmail(String email);

    @Query(value = """
            select u.id, u.email, u.status, u.created_at as "createdAt", count(*) over() as total
            from users u left join profiles p on p.user_id = u.id
            where (:query = '' or u.email ilike :pattern escape '\\' or p.first_name ilike :pattern escape '\\'
                   or p.last_name ilike :pattern escape '\\')
              and (:role = '' or exists (select 1 from user_role_assignments a join roles r on r.id = a.role_id
                                         where a.user_id = u.id and r.code = :role))
            order by u.created_at desc, u.id
            """, nativeQuery = true)
    List<UserSearchRow> search(String query, String pattern, String role, Pageable pageable);

    @Query(value = """
            select count(*) from users u left join profiles p on p.user_id = u.id
            where (:query = '' or u.email ilike :pattern escape '\\' or p.first_name ilike :pattern escape '\\'
                   or p.last_name ilike :pattern escape '\\')
              and (:role = '' or exists (select 1 from user_role_assignments a join roles r on r.id = a.role_id
                                         where a.user_id = u.id and r.code = :role))
            """, nativeQuery = true)
    long countSearch(String query, String pattern, String role);

    boolean existsByEmail(String email);
}
