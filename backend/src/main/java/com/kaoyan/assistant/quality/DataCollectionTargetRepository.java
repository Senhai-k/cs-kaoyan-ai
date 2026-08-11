package com.kaoyan.assistant.quality;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Set;

@Repository
public class DataCollectionTargetRepository {

    private final JdbcTemplate jdbcTemplate;

    public DataCollectionTargetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DataCollectionTarget> findAll() {
        return jdbcTemplate.query("""
                SELECT id, school_id, title, document_type, target_year, source_url,
                  status, note, system_generated, created_at, updated_at
                FROM data_collection_target
                ORDER BY target_year DESC, id ASC
                """, (rs, rowNum) -> new DataCollectionTarget(
                rs.getLong("id"), rs.getLong("school_id"), rs.getString("title"),
                rs.getString("document_type"), rs.getInt("target_year"), rs.getString("source_url"),
                rs.getString("status"), rs.getString("note"), rs.getInt("system_generated") == 1,
                rs.getString("created_at"), rs.getString("updated_at")
        ));
    }

    public DataCollectionTarget findById(Long id) {
        return findAll().stream().filter(target -> target.id().equals(id)).findFirst().orElse(null);
    }

    public DataCollectionTarget findByIdForUpdate(Long id) {
        return jdbcTemplate.query("""
                SELECT id, school_id, title, document_type, target_year, source_url,
                  status, note, system_generated, created_at, updated_at
                FROM data_collection_target WHERE id = ? FOR UPDATE
                """, (rs, rowNum) -> new DataCollectionTarget(
                rs.getLong("id"), rs.getLong("school_id"), rs.getString("title"),
                rs.getString("document_type"), rs.getInt("target_year"), rs.getString("source_url"),
                rs.getString("status"), rs.getString("note"), rs.getInt("system_generated") == 1,
                rs.getString("created_at"), rs.getString("updated_at")
        ), id).stream().findFirst().orElse(null);
    }

    public Long create(Long schoolId, DataCollectionTargetRequest request, boolean systemGenerated) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO data_collection_target (
                      school_id, title, document_type, target_year, source_url,
                      status, note, system_generated
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, schoolId);
            statement.setString(2, request.title());
            statement.setString(3, request.documentType());
            statement.setInt(4, request.targetYear());
            statement.setString(5, request.sourceUrl());
            statement.setString(6, request.status());
            statement.setString(7, request.note());
            statement.setInt(8, systemGenerated ? 1 : 0);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to create collection target");
        }
        return key.longValue();
    }

    public void update(Long id, DataCollectionTargetRequest request) {
        jdbcTemplate.update("""
                UPDATE data_collection_target
                SET title = ?, document_type = ?, target_year = ?, source_url = ?,
                    status = ?, note = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.title(), request.documentType(), request.targetYear(), request.sourceUrl(),
                request.status(), request.note(), id);
    }

    public void markVerified(Long id, String note) {
        jdbcTemplate.update("""
                UPDATE data_collection_target
                SET status = 'VERIFIED', note = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, note, id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM data_collection_target WHERE id = ?", id);
    }

    public int synchronizeSystemTargets(Long schoolId, int targetYear, String officialEntryUrl,
                                        List<String> requiredDocumentTypes) {
        if (officialEntryUrl == null || officialEntryUrl.isBlank()) {
            return 0;
        }
        int changes = 0;
        List<DataCollectionTarget> existing = findAll().stream()
                .filter(target -> target.schoolId().equals(schoolId)
                        && target.targetYear().equals(targetYear) && target.systemGenerated())
                .toList();
        Set<String> required = Set.copyOf(requiredDocumentTypes);
        for (String documentType : required) {
            DataCollectionTarget target = existing.stream()
                    .filter(item -> item.documentType().equals(documentType))
                    .findFirst().orElse(null);
            if (target == null) {
                create(schoolId, new DataCollectionTargetRequest(
                        targetYear + "年" + documentType,
                        documentType,
                        targetYear,
                        officialEntryUrl,
                        "PENDING",
                        "系统根据覆盖缺口生成，需替换为对应官方公告的精确 URL"
                ), true);
                changes++;
            } else if ("VERIFIED".equals(target.status())) {
                jdbcTemplate.update("""
                        UPDATE data_collection_target
                        SET status = 'PENDING', updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, target.id());
                changes++;
            }
        }
        for (DataCollectionTarget target : existing) {
            if (!required.contains(target.documentType()) && !"VERIFIED".equals(target.status())) {
                jdbcTemplate.update("""
                        UPDATE data_collection_target
                        SET status = 'VERIFIED', updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, target.id());
                changes++;
            }
        }
        return changes;
    }
}
