package com.adaptivefit.repository;

import com.adaptivefit.entity.RecommendationDataConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RecommendationDataConsentRepository extends JpaRepository<RecommendationDataConsent, Long> {
    Optional<RecommendationDataConsent> findFirstByUser_IdOrderByRecordedAtDescIdDesc(Long userId);

    @Query(value = """
            SELECT COUNT(*) FROM recommendation_data_consents c
            JOIN (SELECT user_id, MAX(id) AS latest_id
                  FROM recommendation_data_consents GROUP BY user_id) latest
              ON latest.latest_id = c.id
            WHERE c.granted = 1
            """, nativeQuery = true)
    long countUsersWithCurrentConsent();
}
