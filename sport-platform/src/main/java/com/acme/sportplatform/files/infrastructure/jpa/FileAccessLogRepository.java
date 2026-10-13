package com.acme.sportplatform.files.infrastructure.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FileAccessLogRepository extends JpaRepository<FileAccessLogEntity, UUID> {}
