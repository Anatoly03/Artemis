/**
 * A class which encapsulates UI selectors and actions for the exam exercise groups page.
 */
export class ExamExerciseGroupsPage {
    clickCreateNewExerciseGroup() {
        cy.get('#create-new-group').click();
    }

    shouldHaveTitle(groupIndex: number, groupTitle: string) {
        cy.get(`#group-${groupIndex} .group-title`).contains(groupTitle);
    }

    clickEditGroup(groupIndex: number) {
        cy.get(`#group-${groupIndex} .edit-group`).click();
    }

    clickDeleteGroup(groupIndex: number, groupName: string) {
        cy.get(`#group-${groupIndex} .delete-group`).click();
        cy.get('#delete').should('be.disabled');
        cy.get('#confirm-exercise-name').type(groupName);
        cy.get('#delete').should('not.be.disabled').click();
    }

    shouldShowNumberOfExerciseGroups(numberOfGroups: number) {
        cy.get('#number-groups').should('contain.text', numberOfGroups);
    }

    clickAddExerciseGroup() {
        cy.get('#create-new-group').click();
    }

    clickAddTextExercise() {
        cy.get('#add-text-exercise').click();
    }

    visitPageViaUrl(courseId: number, examId: number) {
        cy.visit(`course-management/${courseId}/exams/${examId}/exercise-groups`);
    }

    shouldContainExerciseWithTitle(exerciseTitle: string) {
        cy.get('#exercises').contains(exerciseTitle).scrollIntoView().should('be.visible');
    }
}
