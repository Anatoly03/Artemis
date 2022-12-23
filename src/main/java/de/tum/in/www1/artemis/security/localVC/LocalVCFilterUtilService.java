package de.tum.in.www1.artemis.security.localVC;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class LocalVCFilterUtilService {

    private final Logger log = LoggerFactory.getLogger(LocalVCFilterUtilService.class);

    @Value("${artemis.version-control.url}")
    private URL localVCServerUrl;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public LocalVCFilterUtilService(AuthenticationManagerBuilder authenticationManagerBuilder, UserRepository userRepository, CourseRepository courseRepository,
            AuthorizationCheckService authorizationCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            StudentParticipationRepository studentParticipationRepository) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * @param servletRequest The object containing all information about the incoming request.
     * @param forPush        Whether the method should authenticate a fetch or a push request. For a push request, additional checks are conducted.
     * @throws LocalVCAuthException
     */
    public void authenticateAndAuthorizeGitRequest(HttpServletRequest servletRequest, boolean forPush) throws LocalVCAuthException {

        String basicAuthCredentials = checkAuthorizationHeader(servletRequest.getHeader(LocalVCFilterUtilService.AUTHORIZATION_HEADER));

        String username = basicAuthCredentials.split(":")[0];
        String password = basicAuthCredentials.split(":")[1];

        User user = authenticateUser(username, password);

        String uri = servletRequest.getRequestURI();
        LocalVCRepositoryUrl localVCUrl = validateRepositoryUrl(uri);

        String projectKey = localVCUrl.getProjectKey();
        String courseShortName = localVCUrl.getCourseShortName();
        String repositoryTypeOrUserName = localVCUrl.getRepositoryTypeOrUserName();

        Course course = findCourseForRepository(courseShortName);

        ProgrammingExercise exercise = findExerciseForRepository(projectKey);

        authorizeUser(repositoryTypeOrUserName, course, exercise, user, forPush);

        if (forPush) {
            // TODO: Add Webhooks -> notifies Artemis on Push
            // TODO: Add branch protection (prevent rewriting the history (force-push) and deletion of branches). + no renaming of the repository.
        }
    }

    private String checkAuthorizationHeader(String authorizationHeader) throws LocalVCAuthException {
        if (authorizationHeader == null) {
            throw new LocalVCAuthException();
        }

        String[] basicAuthCredentialsEncoded = authorizationHeader.split(" ");

        if (!basicAuthCredentialsEncoded[0].equals("Basic")) {
            throw new LocalVCAuthException();
        }

        // Return decoded basic auth credentials which contain the username and the password.
        return new String(Base64.getDecoder().decode(basicAuthCredentialsEncoded[1]));
    }

    private User authenticateUser(String username, String password) throws LocalVCAuthException {
        try {
            SecurityUtils.checkUsernameAndPasswordValidity(username, password);

            // Try to authenticate the user.
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
            authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        }
        catch (AccessForbiddenException | AuthenticationException ex) {
            throw new LocalVCAuthException();
        }

        User user = userRepository.findOneByLogin(username).orElse(null);

        // Check that the user exists.
        if (user == null) {
            throw new LocalVCAuthException();
        }

        return user;
    }

    private LocalVCRepositoryUrl validateRepositoryUrl(String urlPath) throws LocalVCBadRequestException {

        String[] pathSplit = urlPath.split("/");

        // Should start with '/git', and end with '.git'.
        if (!pathSplit[1].equals("git") || !(pathSplit[4].endsWith(".git"))) {
            throw new LocalVCBadRequestException("Invalid URL.");
        }

        String repositorySlug = pathSplit[4].replace(".git", "");

        // TODO: Refactor VcsRepositoryUrl and LocalGitRepositoryUrl properly so I do not have to hand an environment variable to the constructor.
        LocalVCRepositoryUrl localVCRepo = new LocalVCRepositoryUrl(localVCServerUrl, pathSplit[3], pathSplit[2], repositorySlug);

        // Project key should contain the course short name.
        if (!localVCRepo.getProjectKey().toLowerCase().contains(localVCRepo.getCourseShortName().toLowerCase())) {
            throw new LocalVCBadRequestException("Badly formed Local Git URI: " + urlPath + " Expected the repository name to start with the lower case course short name.");
        }

        return localVCRepo;
    }

    private Course findCourseForRepository(String courseShortName) throws LocalVCNotFoundException, LocalVCInternalException {
        List<Course> courses = courseRepository.findAllByShortName(courseShortName);
        if (courses.size() != 1) {
            if (courses.size() == 0) {
                throw new LocalVCNotFoundException("No course found for the given short name: " + courseShortName);
            }
            else {
                throw new LocalVCInternalException("Multiple courses found for the given short name: " + courseShortName);
            }
        }
        return courses.get(0);
    }

    private ProgrammingExercise findExerciseForRepository(String projectKey) throws LocalVCNotFoundException, LocalVCInternalException {
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findByProjectKey(projectKey);
        if (exercises.size() != 1) {
            if (exercises.size() == 0) {
                throw new LocalVCNotFoundException("No exercise found for the given project key: " + projectKey);
            }
            else {
                throw new LocalVCInternalException("Multiple exercises found for the given project key: " + projectKey);
            }
        }
        return exercises.get(0);
    }

    private void authorizeUser(String repositoryTypeOrUserName, Course course, ProgrammingExercise exercise, User user, boolean forPush) throws LocalVCAuthException {
        if (isRequestingBaseRepository(repositoryTypeOrUserName)) {
            // ---- Requesting one of the base repositories ("exercise", "tests", or "solution") ----
            // Check that the user is at least an instructor in the course the repository belongs to.
            boolean isAtLeastInstructorInCourse = authorizationCheckService.isAtLeastInstructorInCourse(course, user);
            if (!isAtLeastInstructorInCourse) {
                throw new LocalVCAuthException();
            }
            return;
        }

        // ---- Requesting one of the participant repositories ----

        // Check that the user name in the repository name corresponds to the user name used for Basic Auth.
        if (!user.getLogin().equals(repositoryTypeOrUserName)) {
            throw new LocalVCAuthException();
        }

        // Check that the user is at least a student in the course.
        boolean isAtLeastStudentInCourse = authorizationCheckService.isAtLeastStudentInCourse(course, user);
        if (!isAtLeastStudentInCourse) {
            throw new LocalVCAuthException();
        }

        // Check that the user participates in the exercise the repository belongs to.
        Optional<StudentParticipation> participation = studentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());

        if (participation.isEmpty())
            throw new LocalVCAuthException();

        // Check that the exercise's Release Date is either not set or is in the past.
        if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            throw new LocalVCAuthException();
        }
        if (!forPush)
            return;

        // Additional checks for push request.

        // Check that the exercise's Due Date is either not set or is in the future.
        if (exercise.getDueDate() != null && exercise.getDueDate().isBefore(ZonedDateTime.now())) {
            // Students can still commit code and receive feedback after the exercise due date, if manual review and complaints are not activated. The results for these submissions
            // will not be rated.
            if (exercise.getAssessmentType() == AssessmentType.MANUAL || exercise.getAllowComplaintsForAutomaticAssessments()) {
                throw new LocalVCAuthException();
            }
        }

    }

    private boolean isRequestingBaseRepository(String requestedRepositoryType) {
        for (RepositoryType repositoryType : RepositoryType.values()) {
            if (repositoryType.toString().equals(requestedRepositoryType))
                return true;
        }
        return false;
    }

}