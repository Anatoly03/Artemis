package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.scores.TeamScore;

@Repository
public interface TeamScoreRepository extends JpaRepository<TeamScore, Long> {

    @Query("""
            DELETE
            FROM TeamScore ts
            WHERE ts.team.id= :#{#teamId}
            """)
    @Modifying
    void removeAssociatedWithTeam(@Param("teamId") Long teamId);

    @Query("""
                SELECT ts
                FROM TeamScore ts
                WHERE ts.team.id = :#{#teamId} AND ts.exercise.id = :#{#exerciseId}
            """)
    Optional<TeamScore> findTeamScoreByTeamAndExercise(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);
}
