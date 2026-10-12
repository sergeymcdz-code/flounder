package com.acme.sportplatform.sports.infrastructure.jpa;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "discipline_templates")
public class DisciplineTemplateEntity {

    @Id
    private UUID id;

    @Column(name = "sport_id", nullable = false)
    private UUID sportId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "competition_format", nullable = false, length = 64)
    private String competitionFormat;

    @Column(name = "unit_type", nullable = false, length = 64)
    private String unitType;

    @Column(name = "result_type", nullable = false, length = 64)
    private String resultType;

    @Column(name = "ranking_strategy", nullable = false, length = 64)
    private String rankingStrategy;

    @Column(name = "default_meta", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String defaultMeta;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSportId() {
        return sportId;
    }

    public void setSportId(UUID sportId) {
        this.sportId = sportId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompetitionFormat() {
        return competitionFormat;
    }

    public void setCompetitionFormat(String competitionFormat) {
        this.competitionFormat = competitionFormat;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    public String getRankingStrategy() {
        return rankingStrategy;
    }

    public void setRankingStrategy(String rankingStrategy) {
        this.rankingStrategy = rankingStrategy;
    }

    public String getDefaultMeta() {
        return defaultMeta;
    }

    public void setDefaultMeta(String defaultMeta) {
        this.defaultMeta = defaultMeta;
    }
}