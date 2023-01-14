package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreSchedulerService;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresDTO;

class ExerciseScoresChartIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "exercisescoreschart";

    Long idOfCourse;

    Long idOfTeam1;

    Long idOfTeam2;

    Long idOfStudent1;

    Long idOfStudent2;

    Long idOfStudent3;

    Long idOfIndividualTextExercise;

    Long idOfIndividualTextExerciseWithoutParticipants;

    Long idOfTeamTextExercise;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @BeforeEach
    void setupTestScenario() {
        ParticipantScoreSchedulerService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 50;
        participantScoreSchedulerService.activate();
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        this.database.addUsers(TEST_PREFIX, 3, 2, 0, 0);
        Course course = this.database.createCourse();
        idOfCourse = course.getId();
        TextExercise textExercise = database.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfIndividualTextExercise = textExercise.getId();
        TextExercise exerciseWithoutParticipants = database.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfIndividualTextExerciseWithoutParticipants = exerciseWithoutParticipants.getId();

        Exercise teamExercise = database.createTeamTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfTeamTextExercise = teamExercise.getId();
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        idOfStudent1 = student1.getId();
        User tutor1 = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").get();
        idOfTeam1 = database.createTeam(Set.of(student1), tutor1, teamExercise, TEST_PREFIX + "team1").getId();
        User student2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        idOfStudent2 = student2.getId();
        User student3 = userRepository.findOneByLogin(TEST_PREFIX + "student3").get();
        idOfStudent3 = student3.getId();
        User tutor2 = userRepository.findOneByLogin(TEST_PREFIX + "tutor2").get();
        idOfTeam2 = database.createTeam(Set.of(student2, student3), tutor2, teamExercise, TEST_PREFIX + "team2").getId();

        // Creating result for student1
        database.createParticipationSubmissionAndResult(idOfIndividualTextExercise, student1, 10.0, 10.0, 50, true);
        // Creating result for team1
        Team team1 = teamRepository.findById(idOfTeam1).get();
        database.createParticipationSubmissionAndResult(idOfTeamTextExercise, team1, 10.0, 10.0, 50, true);
        // Creating result for student2
        database.createParticipationSubmissionAndResult(idOfIndividualTextExercise, student2, 10.0, 10.0, 40, true);
        // Creating result for student3
        database.createParticipationSubmissionAndResult(idOfIndividualTextExercise, student3, 10.0, 10.0, 30, true);
        // Creating result for team2
        Team team2 = teamRepository.findById(idOfTeam2).get();
        database.createParticipationSubmissionAndResult(idOfTeamTextExercise, team2, 10.0, 10.0, 90, true);

        await().until(() -> participantScoreRepository.findAllByExercise(textExercise).size() == 3);
        await().until(() -> participantScoreRepository.findAllByExercise(teamExercise).size() == 2);
    }

    @AfterEach
    void tearDown() {
        ParticipantScoreSchedulerService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCourseExerciseScores_asStudent_shouldReturnCorrectIndividualAverageAndMaxScores() throws Exception {
        List<ExerciseScoresDTO> exerciseScores = request.getList(getEndpointUrl(idOfCourse), HttpStatus.OK, ExerciseScoresDTO.class);
        assertThat(exerciseScores).hasSize(3);
        ExerciseScoresDTO individualTextExercise = exerciseScores.stream().filter(exerciseScoresDTO -> exerciseScoresDTO.exerciseId.equals(idOfIndividualTextExercise)).findFirst()
                .get();
        ExerciseScoresDTO teamTextExercise = exerciseScores.stream().filter(exerciseScoresDTO -> exerciseScoresDTO.exerciseId.equals(idOfTeamTextExercise)).findFirst().get();
        ExerciseScoresDTO individualTextExerciseWithoutParticipants = exerciseScores.stream()
                .filter(exerciseScoresDTO -> exerciseScoresDTO.exerciseId.equals(idOfIndividualTextExerciseWithoutParticipants)).findFirst().get();

        assertThat(individualTextExercise.scoreOfStudent).isEqualTo(50.0);
        assertThat(individualTextExercise.averageScoreAchieved).isEqualTo(40.0);
        assertThat(individualTextExercise.maxScoreAchieved).isEqualTo(50);

        assertThat(teamTextExercise.scoreOfStudent).isEqualTo(50.0);
        assertThat(teamTextExercise.averageScoreAchieved).isEqualTo(70.0);
        assertThat(teamTextExercise.maxScoreAchieved).isEqualTo(90.0);

        assertThat(individualTextExerciseWithoutParticipants.scoreOfStudent).isEqualTo(0.0);
        assertThat(individualTextExerciseWithoutParticipants.averageScoreAchieved).isEqualTo(0.0);
        assertThat(individualTextExerciseWithoutParticipants.maxScoreAchieved).isEqualTo(0.0);
    }

    private String getEndpointUrl(long courseId) {
        return String.format("/api/courses/%d/charts/exercise-scores", courseId);
    }
}
