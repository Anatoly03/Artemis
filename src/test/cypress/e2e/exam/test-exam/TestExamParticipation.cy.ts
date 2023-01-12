import { Exam } from 'app/entities/exam.model';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { artemis } from '../../../support/ArtemisTesting';
import dayjs from 'dayjs/esm';
import submission from '../../../fixtures/programming_exercise_submissions/all_successful/submission.json';
import multipleChoiceTemplate from '../../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import { Course } from 'app/entities/course.model';
import { Interception } from 'cypress/types/net-stubbing';
import { generateUUID } from '../../../support/utils';
import { CypressCredentials } from 'src/test/cypress/support/users';

// Requests
const courseRequests = artemis.requests.courseManagement;

// User management
const users = artemis.users;
const student1 = users.getStudentOne();
const student2 = users.getStudentTwo();

// Pageobjects
const courses = artemis.pageobjects.course.list;
const courseOverview = artemis.pageobjects.course.overview;
const examStartEnd = artemis.pageobjects.exam.startEnd;
const examNavigation = artemis.pageobjects.exam.navigationBar;
const onlineEditor = artemis.pageobjects.exercise.programming.editor;
const modelingEditor = artemis.pageobjects.exercise.modeling.editor;
const multipleChoiceQuiz = artemis.pageobjects.exercise.quiz.multipleChoice;
const textEditor = artemis.pageobjects.exercise.text.editor;

// Common primitives
const textExerciseTitle = 'Cypress text exercise';
const packageName = 'de.test';
const examTitle = 'test-exam' + generateUUID();

const titleArray: Array<string> = [];
const quizArray: Array<number> = [];

describe('Exam participation', () => {
    let course: Course;
    let exam: Exam;

    before(() => {
        cy.login(users.getAdmin());
        courseRequests.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .testExam()
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .examMaxPoints(100)
                .numberOfExercises(10)
                .correctionRounds(0)
                .build();
            courseRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                addGroupWithTextExercise(exam);
                addGroupWithTextExercise(exam);
                addGroupWithTextExercise(exam);

                addGroupWithProgrammingExercise(exam);
                addGroupWithProgrammingExercise(exam);

                addGroupWithModelingExercise(exam);
                addGroupWithModelingExercise(exam);

                addGroupWithQuizExercise(exam);
                addGroupWithQuizExercise(exam);
            });
        });
    });

    it('Participates as a student in a registered test exam', () => {
        startParticipation(student1, course, exam);
        openExercise(0);
        makeTextExerciseSubmission();
        openExercise(1);
        makeTextExerciseSubmission();
        openExercise(2);
        makeTextExerciseSubmission();
        openExercise(3);
        makeTextExerciseSubmission();
        openExercise(4);
        makeProgrammingExerciseSubmission();
        openExercise(5);
        makeProgrammingExerciseSubmission();
        openExercise(6);
        makeModelingExerciseSubmission();
        openExercise(7);
        makeModelingExerciseSubmission();
        openExercise(8);
        makeQuizExerciseSubmission(quizArray[0]);
        openExercise(9);
        makeQuizExerciseSubmission(quizArray[1]);

        handInEarly();
        verifyFinalPage();
    });

    it('Checks if switching test exam tabs works', () => {
        startParticipation(student2, course, exam);
        for (let j = 0; j < 20; j++) {
            const i = between(0, titleArray.length);
            openExercise(i);
            checkExerciseTitle(titleArray[i]);
        }

        handInEarly();
        verifyFinalPage();
    });

    after(() => {
        if (course) {
            cy.login(users.getAdmin());
            courseRequests.deleteCourse(course.id!);
        }
    });
});

function addGroupWithTextExercise(exam: Exam) {
    courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
        courseRequests.createTextExercise({ exerciseGroup: groupResponse.body }, textExerciseTitle).then((response) => {
            titleArray.push(response.body.title);
        });
    });
}

function addGroupWithProgrammingExercise(exam: Exam) {
    courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
        courseRequests
            .createProgrammingExercise({ exerciseGroup: groupResponse.body }, undefined, false, undefined, undefined, undefined, undefined, packageName)
            .then((response) => {
                titleArray.push(response.body.title!);
            });
    });
}

function addGroupWithModelingExercise(exam: Exam) {
    courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
        courseRequests.createModelingExercise({ exerciseGroup: groupResponse.body }).then((response) => {
            titleArray.push(response.body.title!);
        });
    });
}

function addGroupWithQuizExercise(exam: Exam) {
    courseRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
        courseRequests.createQuizExercise({ exerciseGroup: groupResponse.body }, [multipleChoiceTemplate]).then((response) => {
            titleArray.push(response.body.title);
            quizArray.push(response.body.quizQuestions![0].id);
        });
    });
}

function startParticipation(student: CypressCredentials, course: Course, exam: Exam) {
    cy.login(student, '/');
    courses.openCourse(course.id!);
    courseOverview.openExamsTab();
    courseOverview.openExam(exam.id!);
    cy.url().should('contain', `/exams/${exam.id}`);
    examStartEnd.startExam();
}

function openExercise(index: number) {
    examNavigation.openExerciseAtIndex(index);
}

function checkExerciseTitle(title: string) {
    cy.get('.exercise-title').should('contain', title);
}

function makeTextExerciseSubmission() {
    cy.fixture('loremIpsum.txt').then((submissionText) => {
        textEditor.typeSubmission(submissionText);
    });
    cy.wait(1000);
}

function makeProgrammingExerciseSubmission() {
    onlineEditor.toggleCompressFileTree();
    onlineEditor.deleteFile('Client.java');
    onlineEditor.deleteFile('BubbleSort.java');
    onlineEditor.deleteFile('MergeSort.java');
    onlineEditor.typeSubmission(submission, 'de.test');
    onlineEditor.submit();
    onlineEditor.getResultScore().contains('100%').and('be.visible');
}

function makeModelingExerciseSubmission() {
    modelingEditor.addComponentToModel(1, false);
    modelingEditor.addComponentToModel(2, false);
    modelingEditor.addComponentToModel(3, false);
}

function makeQuizExerciseSubmission(quizExerciseID: number) {
    multipleChoiceQuiz.tickAnswerOption(0, quizExerciseID);
    multipleChoiceQuiz.tickAnswerOption(2, quizExerciseID);
}

function handInEarly() {
    examNavigation.handInEarly();
    examStartEnd.finishExam().then((request: Interception) => {
        expect(request.response!.statusCode).to.eq(200);
    });
}

function verifyFinalPage() {
    cy.contains(textExerciseTitle).should('be.visible');
    cy.fixture('loremIpsum.txt').then((submissionText) => {
        cy.contains(submissionText).should('be.visible');
    });
}

function between(min: number, max: number) {
    return Math.floor(Math.random() * (max - min + 1) + min);
}
