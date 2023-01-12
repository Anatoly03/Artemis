import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { artemis } from '../../../support/ArtemisTesting';
import { generateUUID } from '../../../support/utils';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// User management
const users = artemis.users;

// Pageobjects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagement = artemis.pageobjects.course.management;
const examManagement = artemis.pageobjects.exam.management;
const textCreation = artemis.pageobjects.exercise.text.creation;
const exerciseGroups = artemis.pageobjects.exam.exerciseGroups;
const exerciseGroupCreation = artemis.pageobjects.exam.exerciseGroupCreation;

// Common primitives
const uid = generateUUID();
const examTitle = 'test-exam' + uid;

describe('Exam management', () => {
    let course: Course;
    let exam: Exam;

    before(() => {
        cy.login(users.getAdmin());
        courseManagementRequests.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequests.addStudentToCourse(course, users.getStudentOne());
            const examConfig = new CypressExamBuilder(course).title(examTitle).testExam().build();
            courseManagementRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
            });
        });
    });

    beforeEach(() => {
        cy.login(users.getAdmin());
    });

    it('Adds an exercise group with a text exercise', () => {
        cy.visit('/');
        navigationBar.openCourseManagement();
        courseManagement.openExamsOfCourse(course.shortName!);
        examManagement.openExerciseGroups(exam.id!);
        exerciseGroups.shouldShowNumberOfExerciseGroups(0);
        exerciseGroups.clickAddExerciseGroup();
        const groupName = 'group 1';
        exerciseGroupCreation.typeTitle(groupName);
        exerciseGroupCreation.isMandatoryBoxShouldBeChecked();
        exerciseGroupCreation.clickSave();
        exerciseGroups.shouldShowNumberOfExerciseGroups(1);
        // Add text exercise
        exerciseGroups.clickAddTextExercise();
        const textExerciseTitle = 'text' + uid;
        textCreation.typeTitle(textExerciseTitle);
        textCreation.typeMaxPoints(10);
        textCreation.create().its('response.statusCode').should('eq', 201);
        exerciseGroups.visitPageViaUrl(course.id!, exam.id!);
        exerciseGroups.shouldContainExerciseWithTitle(textExerciseTitle);
    });

    after(() => {
        if (course) {
            cy.login(users.getAdmin());
            courseManagementRequests.deleteCourse(course.id!);
        }
    });
});
