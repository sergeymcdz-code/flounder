package com.acme.sportplatform.sports.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.sports.SportLookup;
import com.acme.sportplatform.sports.api.CreateDisciplineTemplateRequest;
import com.acme.sportplatform.sports.api.CreateSportRequest;
import com.acme.sportplatform.sports.api.DisciplineTemplateResponse;
import com.acme.sportplatform.sports.api.SportResponse;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.sports.infrastructure.jpa.SportEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.SportRepository;

@Service
public class SportService implements SportLookup {

    private final SportRepository sportRepository;
    private final DisciplineTemplateRepository disciplineTemplateRepository;

    public SportService(
            SportRepository sportRepository,
            DisciplineTemplateRepository disciplineTemplateRepository
    ) {
        this.sportRepository = sportRepository;
        this.disciplineTemplateRepository = disciplineTemplateRepository;
    }

    @Transactional(readOnly = true)
    public List<SportResponse> getSports() {
        return sportRepository.findAll().stream()
                .map(this::mapSport)
                .toList();
    }

    @Transactional
    public SportResponse createSport(CreateSportRequest request) {
        if (sportRepository.existsByCode(request.code())) {
            throw new BusinessException("sports.sport_already_exists", "Sport already exists");
        }

        SportEntity entity = new SportEntity();
        entity.setId(UUID.randomUUID());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setActive(true);

        return mapSport(sportRepository.save(entity));
    }

    @Transactional
    public DisciplineTemplateResponse createDisciplineTemplate(CreateDisciplineTemplateRequest request) {
        if (!sportRepository.existsById(request.sportId())) {
            throw new BusinessException("sports.sport_not_found", "Sport not found");
        }

        if (disciplineTemplateRepository.existsBySportIdAndCode(request.sportId(), request.code())) {
            throw new BusinessException(
                    "sports.discipline_template_already_exists",
                    "Discipline template already exists"
            );
        }

        DisciplineTemplateEntity entity = new DisciplineTemplateEntity();
        entity.setId(UUID.randomUUID());
        entity.setSportId(request.sportId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setCompetitionFormat(request.competitionFormat());
        entity.setUnitType(request.unitType());
        entity.setResultType(request.resultType());
        entity.setRankingStrategy(request.rankingStrategy());
        entity.setDefaultMeta("{}");

        return mapDiscipline(disciplineTemplateRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<DisciplineTemplateResponse> getDisciplineTemplates() {
        return disciplineTemplateRepository.findAll().stream()
                .map(this::mapDiscipline)
                .toList();
    }

    private SportResponse mapSport(SportEntity entity) {
        return new SportResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.isActive()
        );
    }

    private DisciplineTemplateResponse mapDiscipline(DisciplineTemplateEntity entity) {
        return new DisciplineTemplateResponse(
                entity.getId(),
                entity.getSportId(),
                entity.getCode(),
                entity.getName(),
                entity.getCompetitionFormat(),
                entity.getUnitType(),
                entity.getResultType(),
                entity.getRankingStrategy(),
                entity.getDefaultMeta()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID sportId) {
        return sportRepository.existsById(sportId);
    }
}