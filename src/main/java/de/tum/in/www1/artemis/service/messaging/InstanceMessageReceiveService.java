package de.tum.in.www1.artemis.service.messaging;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.scheduled.*;
import de.tum.in.www1.artemis.service.scheduled.cache.monitoring.ExamMonitoringScheduleService;

/**
 * This service is only available on a node with the 'scheduling' profile.
 * It receives messages from Hazelcast whenever another node sends a message to a specific topic and processes it on this node.
 */
@Service
@Profile("scheduling")
public class InstanceMessageReceiveService {

    private final Logger log = LoggerFactory.getLogger(InstanceMessageReceiveService.class);

    private final ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    private final ModelingExerciseScheduleService modelingExerciseScheduleService;

    private final ExamMonitoringScheduleService examMonitoringScheduleService;

    private final NotificationScheduleService notificationScheduleService;

    private final ParticipantScoreScheduleService participantScoreScheduleService;

    private final Optional<AtheneScheduleService> atheneScheduleService;

    private final UserScheduleService userScheduleService;

    private final TextExerciseRepository textExerciseRepository;

    private final ExerciseRepository exerciseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final UserRepository userRepository;

    public InstanceMessageReceiveService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseScheduleService programmingExerciseScheduleService,
            ModelingExerciseRepository modelingExerciseRepository, ModelingExerciseScheduleService modelingExerciseScheduleService,
            ExamMonitoringScheduleService examMonitoringScheduleService, TextExerciseRepository textExerciseRepository, ExerciseRepository exerciseRepository,
            Optional<AtheneScheduleService> atheneScheduleService, HazelcastInstance hazelcastInstance, UserRepository userRepository, UserScheduleService userScheduleService,
            NotificationScheduleService notificationScheduleService, ParticipantScoreScheduleService participantScoreScheduleService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseScheduleService = programmingExerciseScheduleService;
        this.examMonitoringScheduleService = examMonitoringScheduleService;
        this.textExerciseRepository = textExerciseRepository;
        this.atheneScheduleService = atheneScheduleService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingExerciseScheduleService = modelingExerciseScheduleService;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.userScheduleService = userScheduleService;
        this.notificationScheduleService = notificationScheduleService;
        this.participantScoreScheduleService = participantScoreScheduleService;

        hazelcastInstance.<Long>getTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleProgrammingExercise((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE_CANCEL.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleProgrammingExerciseCancel(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.MODELING_EXERCISE_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleModelingExercise((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.MODELING_EXERCISE_SCHEDULE_CANCEL.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleModelingExerciseCancel(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.MODELING_EXERCISE_INSTANT_CLUSTERING.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processModelingExerciseInstantClustering((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.TEXT_EXERCISE_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleTextExercise((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.TEXT_EXERCISE_SCHEDULE_CANCEL.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processTextExerciseScheduleCancel((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.TEXT_EXERCISE_INSTANT_CLUSTERING.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processTextExerciseInstantClustering((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processUnlockAllRepositories((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.PROGRAMMING_EXERCISE_LOCK_REPOSITORIES.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processLockAllRepositories((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_WITHOUT_EARLIER_DUE_DATE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processUnlockAllRepositoriesWithoutEarlierIndividualDueDate(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.PROGRAMMING_EXERCISE_LOCK_WITHOUT_LATER_DUE_DATE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processLockAllRepositoriesWithoutLaterIndividualDueDate(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USERS.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processRemoveNonActivatedUser((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.USER_MANAGEMENT_CANCEL_REMOVE_NON_ACTIVATED_USERS.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processCancelRemoveNonActivatedUser((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.EXERCISE_RELEASED_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleExerciseReleasedNotification((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.ASSESSED_EXERCISE_SUBMISSION_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleAssessedExerciseSubmittedNotification((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.EXAM_MONITORING_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleExamMonitoring(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.EXAM_MONITORING_SCHEDULE_CANCEL.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleExamMonitoringCancel(message.getMessageObject());
        });
        hazelcastInstance.<Long[]>getTopic(MessageTopic.PARTICIPANT_SCORE_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleParticipantScore(message.getMessageObject()[0], message.getMessageObject()[1], message.getMessageObject()[2]);
        });
    }

    public void processScheduleProgrammingExercise(Long exerciseId) {
        log.info("Received schedule update for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        programmingExerciseScheduleService.updateScheduling(programmingExercise);
    }

    public void processScheduleProgrammingExerciseCancel(Long exerciseId) {
        log.info("Received schedule cancel for programming exercise {}", exerciseId);
        // The exercise might already be deleted, so we can not get it from the database.
        // Use the ID directly instead.
        programmingExerciseScheduleService.cancelAllScheduledTasks(exerciseId);
    }

    public void processScheduleModelingExercise(Long exerciseId) {
        log.info("Received schedule update for modeling exercise {}", exerciseId);
        ModelingExercise modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);
        modelingExerciseScheduleService.updateScheduling(modelingExercise);
    }

    public void processScheduleModelingExerciseCancel(Long exerciseId) {
        log.info("Received schedule cancel for modeling exercise {}", exerciseId);
        // The exercise might already be deleted, so we can not get it from the database.
        // Use the ID directly instead.
        modelingExerciseScheduleService.cancelAllScheduledTasks(exerciseId);
    }

    public void processModelingExerciseInstantClustering(Long exerciseId) {
        log.info("Received schedule instant clustering for modeling exercise {}", exerciseId);
        ModelingExercise modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);
        modelingExerciseScheduleService.scheduleExerciseForInstant(modelingExercise);
    }

    public void processScheduleTextExercise(Long exerciseId) {
        log.info("Received schedule update for text exercise {}", exerciseId);
        TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        atheneScheduleService.ifPresent(service -> service.scheduleExerciseForAtheneIfRequired(textExercise));
    }

    public void processTextExerciseScheduleCancel(Long exerciseId) {
        log.info("Received schedule cancel for text exercise {}", exerciseId);
        atheneScheduleService.ifPresent(service -> service.cancelScheduledAthene(exerciseId));
    }

    public void processTextExerciseInstantClustering(Long exerciseId) {
        log.info("Received schedule instant clustering for text exercise {}", exerciseId);
        TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        atheneScheduleService.ifPresent(service -> service.scheduleExerciseForInstantAthene(textExercise));
    }

    public void processUnlockAllRepositories(Long exerciseId) {
        log.info("Received unlock all repositories for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are unlocked as fast as possible
        programmingExerciseScheduleService.unlockAllStudentRepositories(programmingExercise).run();
    }

    public void processLockAllRepositories(Long exerciseId) {
        log.info("Received lock all repositories for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are locked as fast as possible
        programmingExerciseScheduleService.lockAllStudentRepositories(programmingExercise).run();
    }

    /**
     * Unlocks all repositories that do not have an individual due date before now
     * @param exerciseId the id of the programming exercises where the repos should be unlocked
     */
    public void processUnlockAllRepositoriesWithoutEarlierIndividualDueDate(Long exerciseId) {
        log.info("Received unlock all repositories without an individual due date before now for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are locked as fast as possible
        programmingExerciseScheduleService.unlockStudentRepositories(programmingExercise, participation -> {
            ZonedDateTime individualDueDate = participation.getIndividualDueDate();
            return individualDueDate == null || individualDueDate.isAfter(ZonedDateTime.now());
        }).run();
    }

    /**
     * Locks all repositories that do not have an individual due date after now
     * @param exerciseId the id of the programming exercises where the repos should be locked
     */
    public void processLockAllRepositoriesWithoutLaterIndividualDueDate(Long exerciseId) {
        log.info("Received lock all repositories without an individual due date after now for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are locked as fast as possible
        programmingExerciseScheduleService.lockStudentRepositories(programmingExercise, participation -> {
            ZonedDateTime individualDueDate = participation.getIndividualDueDate();
            return individualDueDate == null || individualDueDate.isBefore(ZonedDateTime.now());
        }).run();
    }

    public void processRemoveNonActivatedUser(Long userId) {
        log.info("Received remove non-activated user for user {}", userId);
        User user = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(userId);
        userScheduleService.scheduleForRemoveNonActivatedUser(user);
    }

    public void processCancelRemoveNonActivatedUser(Long userId) {
        log.info("Received cancel removal of non-activated user for user {}", userId);
        User user = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(userId);
        userScheduleService.cancelScheduleRemoveNonActivatedUser(user);
    }

    public void processScheduleExerciseReleasedNotification(Long exerciseId) {
        log.info("Received schedule update for exercise {} released notification ", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        notificationScheduleService.updateSchedulingForReleasedExercises(exercise);
    }

    public void processScheduleAssessedExerciseSubmittedNotification(Long exerciseId) {
        log.info("Received schedule update for assessed exercise submitted {} notification ", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        notificationScheduleService.updateSchedulingForAssessedExercisesSubmissions(exercise);
    }

    public void processScheduleExamMonitoring(Long examId) {
        log.info("Received schedule update for exam monitoring {}", examId);
        examMonitoringScheduleService.scheduleExamMonitoringTask(examId);
    }

    public void processScheduleExamMonitoringCancel(Long examId) {
        log.info("Received schedule cancel for exam monitoring {}", examId);
        examMonitoringScheduleService.cancelExamMonitoringTask(examId);
    }

    public void processScheduleParticipantScore(Long exerciseId, Long participantId, Long resultIdToBeDeleted) {
        log.info("Received schedule participant score for exercise {} and participant {} (result to be deleted: {})", exerciseId, participantId, resultIdToBeDeleted);
        participantScoreScheduleService.scheduleTask(exerciseId, participantId, resultIdToBeDeleted);
    }

}
