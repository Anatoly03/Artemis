package de.tum.in.www1.artemis.connectors;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.connectors.Lti10Service;
import de.tum.in.www1.artemis.service.connectors.Lti13Service;
import de.tum.in.www1.artemis.service.connectors.LtiNewResultService;

class LtiNewResultServiceTest {

    @Mock
    private Lti10Service lti10Service;

    @Mock
    private Lti13Service lti13Service;

    private LtiNewResultService ltiNewResultService;

    private StudentParticipation participation;

    private Course course;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        ltiNewResultService = new LtiNewResultService(lti10Service, lti13Service);

        participation = new StudentParticipation();
        Exercise exercise = new TextExercise();
        participation.setExercise(exercise);
        course = new Course();
        exercise.setCourse(course);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(lti10Service, lti13Service);
    }

    @Test
    void onNewResult_notOnlineCourse() {
        course.setOnlineCourse(false);

        ltiNewResultService.onNewResult(participation);

        verifyNoInteractions(lti10Service);
        verifyNoInteractions(lti13Service);
    }

    @Test
    void onNewResult_onlineCourse() {
        course.setOnlineCourse(true);

        ltiNewResultService.onNewResult(participation);

        verify(lti10Service, times(1)).onNewResult(participation);
        verify(lti13Service, times(1)).onNewResult(participation);
    }
}
