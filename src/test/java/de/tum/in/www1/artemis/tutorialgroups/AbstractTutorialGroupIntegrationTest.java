package de.tum.in.www1.artemis.tutorialgroups;

import static de.tum.in.www1.artemis.tutorialgroups.AbstractTutorialGroupIntegrationTest.RandomTutorialGroupGenerator.generateRandomTitle;
import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.*;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupService;
import de.tum.in.www1.artemis.util.CourseTestService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

/**
 * Contains useful methods for testing the tutorial groups feature.
 */
abstract class AbstractTutorialGroupIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseTestService courseTestService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    DatabaseUtilService databaseUtilService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    TutorialGroupSessionRepository tutorialGroupSessionRepository;

    @Autowired
    TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    @Autowired
    TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    @Autowired
    TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    @Autowired
    TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    @Autowired
    TutorialGroupNotificationRepository tutorialGroupNotificationRepository;

    @Autowired
    TutorialGroupService tutorialGroupService;

    Long exampleCourseId;

    Long exampleConfigurationId;

    String exampleTimeZone = "Europe/Bucharest";

    String testPrefix = "";

    Integer defaultSessionStartHour = 10;

    Integer defaultSessionEndHour = 12;

    LocalDate firstAugustMonday = LocalDate.of(2022, 8, 1);

    LocalDate secondAugustMonday = LocalDate.of(2022, 8, 8);

    LocalDate thirdAugustMonday = LocalDate.of(2022, 8, 15);

    LocalDate fourthAugustMonday = LocalDate.of(2022, 8, 22);

    LocalDate firstSeptemberMonday = LocalDate.of(2022, 9, 5);

    @BeforeEach
    void setupTestScenario() {
        this.testPrefix = getTestPrefix();
        var course = this.database.createCourse();
        course.setTimeZone(exampleTimeZone);
        courseRepository.save(course);
        exampleCourseId = course.getId();
        exampleConfigurationId = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1)).getId();
    }

    // === Abstract Methods ===
    abstract String getTestPrefix();

    // === Paths ===
    String getTutorialGroupsPath(Long courseId) {
        return "/api/courses/" + courseId + "/tutorial-groups/";
    }

    String getTutorialGroupsConfigurationPath(Long courseId) {
        return "/api/courses/" + courseId + "/tutorial-groups-configuration/";
    }

    String getTutorialGroupFreePeriodsPath() {
        return this.getTutorialGroupsConfigurationPath(exampleCourseId) + exampleConfigurationId + "/tutorial-free-periods/";
    }

    String getSessionsPathOfDefaultTutorialGroup(Long tutorialGroupId) {
        return this.getTutorialGroupsPath(this.exampleCourseId) + tutorialGroupId + "/sessions/";
    }

    String getSessionsPathOfTutorialGroup(Long tutorialGroupId) {
        return this.getTutorialGroupsPath(this.exampleCourseId) + tutorialGroupId + "/sessions/";
    }

    // === UTILS ===
    TutorialGroupSession buildAndSaveExampleIndividualTutorialGroupSession(Long tutorialGroupId, LocalDate localDate) {
        return databaseUtilService.createIndividualTutorialGroupSession(tutorialGroupId, getExampleSessionStartOnDate(localDate), getExampleSessionEndOnDate(localDate));
    }

    TutorialGroupsConfiguration buildExampleConfiguration(Long courseId) {
        TutorialGroupsConfiguration tutorialGroupsConfiguration = new TutorialGroupsConfiguration();
        tutorialGroupsConfiguration.setCourse(courseRepository.findById(courseId).get());
        tutorialGroupsConfiguration.setTutorialPeriodStartInclusive(firstAugustMonday.toString());
        tutorialGroupsConfiguration.setTutorialPeriodEndInclusive(firstSeptemberMonday.toString());
        return tutorialGroupsConfiguration;
    }

    TutorialGroupSchedule buildExampleSchedule(LocalDate validFromInclusive, LocalDate validToInclusive) {
        TutorialGroupSchedule newTutorialGroupSchedule = new TutorialGroupSchedule();
        newTutorialGroupSchedule.setDayOfWeek(1);
        newTutorialGroupSchedule.setStartTime("10:00:00");
        newTutorialGroupSchedule.setEndTime("12:00:00");
        newTutorialGroupSchedule.setValidFromInclusive(validFromInclusive.toString());
        newTutorialGroupSchedule.setValidToInclusive(validToInclusive.toString());
        newTutorialGroupSchedule.setLocation("LoremIpsum");
        newTutorialGroupSchedule.setRepetitionFrequency(1);
        return newTutorialGroupSchedule;
    }

    TutorialGroup buildAndSaveTutorialGroupWithoutSchedule(String tutorLogin, String... studentLogins) {
        Set<User> students = Set.of();
        if (studentLogins != null) {
            students = Arrays.stream(studentLogins).map(login -> userRepository.findOneByLogin(login).get()).collect(Collectors.toSet());
        }
        return databaseUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum", 10, false, "Garching", Language.ENGLISH,
                userRepository.findOneByLogin(testPrefix + tutorLogin).get(), students);
    }

    TutorialGroup buildTutorialGroupWithoutSchedule(String tutorLogin) {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setTitle(generateRandomTitle());
        tutorialGroup.setTeachingAssistant(userRepository.findOneByLogin(testPrefix + tutorLogin).get());
        return tutorialGroup;
    }

    TutorialGroup buildTutorialGroupWithExampleSchedule(LocalDate validFromInclusive, LocalDate validToInclusive, String tutorLogin) {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var newTutorialGroup = new TutorialGroup();
        newTutorialGroup.setCourse(course);
        newTutorialGroup.setTitle(generateRandomTitle());
        newTutorialGroup.setTeachingAssistant(userRepository.findOneByLogin(testPrefix + tutorLogin).get());

        newTutorialGroup.setTutorialGroupSchedule(this.buildExampleSchedule(validFromInclusive, validToInclusive));

        return newTutorialGroup;
    }

    TutorialGroup setUpTutorialGroupWithSchedule(Long courseId, String tutorLogin) throws Exception {
        var newTutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, tutorLogin);
        var scheduleToCreate = newTutorialGroup.getTutorialGroupSchedule();
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(courseId), newTutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();

        newTutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        this.assertTutorialGroupPersistedWithSchedule(newTutorialGroup, scheduleToCreate);
        return newTutorialGroup;
    }

    List<TutorialGroupSession> getTutorialGroupSessionsAscending(Long tutorialGroupId) {
        var sessions = new ArrayList<>(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroupId).stream().toList());
        sessions.sort(Comparator.comparing(TutorialGroupSession::getStart));
        return sessions;
    }

    ZonedDateTime getExampleSessionStartOnDate(LocalDate date) {
        return getDateTimeInExampleTimeZone(date, defaultSessionStartHour);
    }

    ZonedDateTime getExampleSessionEndOnDate(LocalDate date) {
        return getDateTimeInExampleTimeZone(date, defaultSessionEndHour);
    }

    ZonedDateTime getDateTimeInExampleTimeZone(LocalDate date, int hour) {
        return ZonedDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, 0, 0, 0, ZoneId.of(this.exampleTimeZone));
    }

    ZonedDateTime getDateTimeInBerlinTimeZone(LocalDate date, int hour) {
        return ZonedDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, 0, 0, 0, ZoneId.of("Europe/Berlin"));
    }

    // === ASSERTIONS ===

    void assertIndividualSessionIsActiveOnDate(TutorialGroupSession sessionToCheck, LocalDate date, Long tutorialGroupId) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.empty(), tutorialGroupId, getExampleSessionStartOnDate(date), getExampleSessionEndOnDate(date),
                "LoremIpsum", TutorialGroupSessionStatus.ACTIVE, null);
    }

    void assertIndividualSessionIsCancelledOnDate(TutorialGroupSession sessionToCheck, LocalDate date, Long tutorialGroupId, String statusExplanation) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.empty(), tutorialGroupId, getExampleSessionStartOnDate(date), getExampleSessionEndOnDate(date),
                "LoremIpsum", TutorialGroupSessionStatus.CANCELLED, statusExplanation);
    }

    void assertScheduledSessionIsActiveOnDate(TutorialGroupSession sessionToCheck, LocalDate date, Long tutorialGroupId, TutorialGroupSchedule schedule) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.of(schedule.getId()), tutorialGroupId, getExampleSessionStartOnDate(date),
                getExampleSessionEndOnDate(date), schedule.getLocation(), TutorialGroupSessionStatus.ACTIVE, null);
    }

    void assertScheduledSessionIsCancelledOnDate(TutorialGroupSession sessionToCheck, LocalDate date, Long tutorialGroupId, TutorialGroupSchedule schedule) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.of(schedule.getId()), tutorialGroupId, getExampleSessionStartOnDate(date),
                getExampleSessionEndOnDate(date), schedule.getLocation(), TutorialGroupSessionStatus.CANCELLED, null);
    }

    void assertTutorialGroupPersistedWithSchedule(TutorialGroup tutorialGroupToCheck, TutorialGroupSchedule expectedSchedule) {
        assertThat(tutorialGroupToCheck.getId()).isNotNull();
        assertThat(tutorialGroupToCheck.getTutorialGroupSchedule()).isNotNull();
        assertThat(tutorialGroupToCheck.getTutorialGroupSchedule().getId()).isNotNull();
        assertThat(tutorialGroupToCheck.getTutorialGroupSchedule().sameSchedule(expectedSchedule)).isTrue();
    }

    void assertTutorialGroupSessionProperties(TutorialGroupSession tutorialGroupSessionToCheck, Optional<Long> expectedScheduleId, Long expectedTutorialGroupId,
            ZonedDateTime expectedStart, ZonedDateTime expectedEnd, String expectedLocation, TutorialGroupSessionStatus expectedStatus, String expectedStatusExplanation) {
        assertThat(tutorialGroupSessionToCheck.getStart()).isEqualTo(expectedStart);
        assertThat(tutorialGroupSessionToCheck.getEnd()).isEqualTo(expectedEnd);
        assertThat(tutorialGroupSessionToCheck.getTutorialGroup().getId()).isEqualTo(expectedTutorialGroupId);
        expectedScheduleId.ifPresent(scheduleId -> assertThat(tutorialGroupSessionToCheck.getTutorialGroupSchedule().getId()).isEqualTo(scheduleId));
        assertThat(tutorialGroupSessionToCheck.getLocation()).isEqualTo(expectedLocation);
        assertThat(tutorialGroupSessionToCheck.getStatus()).isEqualTo(expectedStatus);
        assertThat(tutorialGroupSessionToCheck.getStatusExplanation()).isEqualTo(expectedStatusExplanation);
    }

    void assertConfigurationStructure(TutorialGroupsConfiguration configuration, LocalDate expectedPeriodStart, LocalDate expectedPeriodEnd, Long courseId) {
        assertThat(configuration.getCourse().getId()).isEqualTo(courseId);
        assertThat(LocalDate.parse(configuration.getTutorialPeriodStartInclusive())).isEqualTo(expectedPeriodStart);
        assertThat(LocalDate.parse(configuration.getTutorialPeriodEndInclusive())).isEqualTo(expectedPeriodEnd);
    }

    public static class RandomTutorialGroupGenerator {

        private static final String LOWERCASE_LETTERS = "abcdefghijklmnopqrstuvwxyz";

        private static final String NUMBERS = "0123456789";

        private static final String ALL_CHARS = LOWERCASE_LETTERS + NUMBERS;

        private static final SecureRandom RANDOM = new SecureRandom();

        public static String generateRandomTitle() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append(ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length())));
            }
            return sb.toString();
        }
    }

}
