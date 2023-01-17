import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import allSuccessful from '../../../fixtures/programming_exercise_submissions/all_successful/submission.json';
import partiallySuccessful from '../../../fixtures/programming_exercise_submissions/partially_successful/submission.json';
import buildError from '../../../fixtures/programming_exercise_submissions/build_error/submission.json';
import { artemis } from '../../../support/ArtemisTesting';
import { makeSubmissionAndVerifyResults, startParticipationInProgrammingExercise } from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';

// Users
const users = artemis.users;
const admin = users.getAdmin();
const studentOne = users.getStudentOne();
const studentTwo = users.getStudentTwo();
const studentThree = users.getStudentThree();

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// PageObjects
const editorPage = artemis.pageobjects.exercise.programming.editor;

describe('Programming exercise participations', () => {
    let course: Course;
    let exercise: ProgrammingExercise;

    before(() => {
        setupCourseAndProgrammingExercise();
    });

    it('Makes a failing submission', () => {
        startParticipationInProgrammingExercise(course.id!, exercise.id!, studentOne);
        makeFailingSubmission(exercise.id!);
    });

    it('Makes a partially successful submission', () => {
        startParticipationInProgrammingExercise(course.id!, exercise.id!, studentTwo);
        makePartiallySuccessfulSubmission(exercise.id!);
    });

    it('Makes a successful submission', () => {
        startParticipationInProgrammingExercise(course.id!, exercise.id!, studentThree);
        makeSuccessfulSubmission(exercise.id!);
    });

    after(() => {
        if (course) {
            cy.login(admin);
            courseManagementRequests.deleteCourse(course.id!);
        }
    });

    /**
     * Creates a course and a programming exercise inside that course.
     */
    function setupCourseAndProgrammingExercise() {
        cy.login(admin, '/');
        courseManagementRequests.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequests.addStudentToCourse(course, studentOne);
            courseManagementRequests.addStudentToCourse(course, studentTwo);
            courseManagementRequests.addStudentToCourse(course, studentThree);
            courseManagementRequests.createProgrammingExercise({ course }).then((exerciseResponse) => {
                exercise = exerciseResponse.body;
            });
        });
    }

    /**
     * Makes a submission, which fails the CI build and asserts that this is highlighted in the UI.
     */
    function makeFailingSubmission(exerciseID: number) {
        makeSubmissionAndVerifyResults(exerciseID, editorPage, exercise.packageName!, buildError, () => {
            editorPage.getResultScore().contains('Build failed').and('be.visible');
            editorPage.getResultScore().contains('0%').and('be.visible');
        });
    }

    /**
     * Makes a submission, which passes and fails some tests, and asserts the outcome in the UI.
     */
    function makePartiallySuccessfulSubmission(exerciseID: number) {
        makeSubmissionAndVerifyResults(exerciseID, editorPage, exercise.packageName!, partiallySuccessful, () => {
            editorPage.getResultScore().contains('46.2%').and('be.visible');
        });
    }

    /**
     * Makes a submission, which passes all tests, and asserts the outcome in the UI.
     */
    function makeSuccessfulSubmission(exerciseID: number) {
        makeSubmissionAndVerifyResults(exerciseID, editorPage, exercise.packageName!, allSuccessful, () => {
            editorPage.getResultScore().contains('100%').and('be.visible');
        });
    }
});
