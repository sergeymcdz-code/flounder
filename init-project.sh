#!/bin/bash

# Корневая папка проекта
ROOT_DIR="sport-platform"
mkdir -p "$ROOT_DIR"
cd "$ROOT_DIR" || exit

# 1. Создание корневых файлов
echo "<!-- Maven POM -->" > pom.xml
echo "services:" > compose.yaml
echo "ENV=local" > .env
echo "# Sport Platform" > README.md

# 2. Создание папки docs и подпапок
mkdir -p docs/architecture docs/decisions docs/api

# 3. Создание папки scripts и подпапок
mkdir -p scripts/dev scripts/ci

# 4. Создание структуры исходного кода (Java пакеты)
BASE_PKG="src/main/java/com/acme/sportplatform"
MODULES=(
  "bootstrap" "common" "config" "identity" "organization" 
  "catalog" "regulation" "event" "registration" "competition" 
  "results" "notification" "analytics" "admin"
)

# Создаем базовый пакет и модули Spring Modulith
mkdir -p "$BASE_PKG"
for mod in "${MODULES[@]}"; do
  mkdir -p "$BASE_PKG/$mod"
done

# Создаем главный класс Spring Boot
cat <<EOF > "$BASE_PKG/SportPlatformApplication.java"
package com.acme.sportplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SportPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(SportPlatformApplication.class, args);
    }
}
EOF

# 5. Создание ресурсов (main resources)
RES_DIR="src/main/resources"
mkdir -p "$RES_DIR/db/migration"
touch "$RES_DIR/application.yml"
touch "$RES_DIR/application-local.yml"
touch "$RES_DIR/application-dev.yml"
touch "$RES_DIR/application-test.yml"
touch "$RES_DIR/logback-spring.xml"
echo "Sport Platform" > "$RES_DIR/banner.txt"

# 6. Создание структуры тестов (test)
TEST_PKG="src/test/java/com/acme/sportplatform"
mkdir -p "$TEST_PKG/architecture"
mkdir -p "$TEST_PKG/integration"
mkdir -p "$TEST_PKG/modulith"
mkdir -p "$TEST_PKG/support"
mkdir -p "src/test/resources"

# Создаем базовый тест архитектуры Spring Modulith
cat <<EOF > "$TEST_PKG/modulith/ModulithArchitectureTest.java"
package com.acme.sportplatform.modulith;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    ApplicationModules modules = ApplicationModules.of(SportPlatformApplication.class);

    @Test
    void verifyArchitecture() {
        modules.verify();
    }
}
EOF

# 7. Создание GitHub Workflows
mkdir -p .github/workflows

echo "--------------------------------------------------------"
echo "Структура проекта успешно создана в папке /$ROOT_DIR!"
echo "Также добавлен тест архитектуры ModulithArchitectureTest.java"
echo "--------------------------------------------------------"