import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { Component, Injectable, Input } from '@angular/core';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { ResultWithPointsPerGradingCriterion } from 'app/entities/result-with-points-per-grading-criterion.model';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { ExportToCsv } from 'export-to-csv';

interface TestCaseResult {
    testName: string;
    testResult: string;
}

@Component({
    selector: 'jhi-exercise-scores-export-button',
    template: `
        <div ngbDropdown class="d-none d-md-inline">
            <button id="export-results-dropdown" class="btn btn-info btn-sm me-1" ngbDropdownToggle>
                <fa-icon [icon]="faDownload"></fa-icon>
                <span class="d-none d-md-inline" jhiTranslate="artemisApp.exercise.exportResults">Export Results</span>
            </button>
            <div ngbDropdownMenu="export-results-dropdown">
                <button ngbDropdownItem (click)="exportResults(true)">With test cases</button>
                <button ngbDropdownItem (click)="exportResults(false)">Without test cases</button>
            </div>
        </div>
    `,
})
@Injectable({ providedIn: 'root' })
export class ExerciseScoresExportButtonComponent {
    @Input() exercises: Exercise[] = []; // Used to export multiple scores together
    @Input() exercise: Exercise | ProgrammingExercise;

    // Icons
    faDownload = faDownload;

    constructor(private resultService: ResultService, private alertService: AlertService) {}

    /**
     * Exports the exercise results as a CSV file.
     */
    exportResults(withTestCases: boolean) {
        if (this.exercises.length === 0 && this.exercise !== undefined) {
            this.exercises = this.exercises.concat(this.exercise);
        }

        this.exercises.forEach((exercise) => this.constructCSV(exercise, withTestCases));
    }

    /**
     * Builds the CSV with results and triggers the download to the user for it.
     * @param exercise for which the results should be exported.
     * @param withTestCases optional parameter that includes test cases info in the exported CSV file
     * @private
     */
    private constructCSV(exercise: Exercise, withTestCases?: boolean) {
        this.resultService.getResultsWithPointsPerGradingCriterion(exercise).subscribe((data) => {
            const results: ResultWithPointsPerGradingCriterion[] = data.body || [];
            if (results.length === 0) {
                this.alertService.warning(`artemisApp.exercise.exportResultsEmptyError`, { exercise: exercise.title });
                window.scroll(0, 0);
                return;
            }
            const isTeamExercise = !!(results[0].result.participation! as StudentParticipation).team;
            const gradingCriteria: GradingCriterion[] = ExerciseScoresExportButtonComponent.sortedGradingCriteria(exercise);

            let keys;
            let rows;
            if (withTestCases && results.first()) {
                const testCasesNames = this.getTestCaseNamesFromResult(results.first()!);
                keys = ExerciseScoresRowBuilder.keys(exercise, isTeamExercise, gradingCriteria, testCasesNames);
                rows = results.map((resultWithPoints) => {
                    const studentParticipation = resultWithPoints.result.participation! as StudentParticipation;
                    const testCaseResults = this.getTestCaseResults(resultWithPoints, testCasesNames);
                    return new ExerciseScoresRowBuilder(exercise, gradingCriteria, studentParticipation, resultWithPoints, testCaseResults).build();
                });
            } else {
                keys = ExerciseScoresRowBuilder.keys(exercise, isTeamExercise, gradingCriteria);
                rows = results.map((resultWithPoints) => {
                    const studentParticipation = resultWithPoints.result.participation! as StudentParticipation;
                    return new ExerciseScoresRowBuilder(exercise, gradingCriteria, studentParticipation, resultWithPoints).build();
                });
            }
            const fileNamePrefix = exercise.shortName ?? exercise.title?.split(/\s+/).join('_');
            ExerciseScoresExportButtonComponent.exportAsCsv(`${fileNamePrefix}-results-scores`, keys, rows, withTestCases ? ',' : undefined);
        });
    }

    /**
     * Triggers the download as CSV for the exercise results.
     * @param filename The filename the results should be downloaded as.
     * @param keys The column names in the CSV.
     * @param rows The actual data rows in the CSV.
     * @param fieldSeparator Optional parameter for exporting the CSV file using a custom separator symbol
     * @private
     */
    private static exportAsCsv(filename: string, keys: string[], rows: ExerciseScoresRow[], fieldSeparator?: string) {
        const options = {
            fieldSeparator: fieldSeparator ?? ';',
            quoteStrings: '"',
            decimalSeparator: 'locale',
            showLabels: true,
            showTitle: false,
            filename,
            useTextFile: false,
            useBom: true,
            headers: keys,
        };

        const csvExporter = new ExportToCsv(options);
        csvExporter.generateCsv(rows); // includes download
    }

    /**
     * Sorts the list of grading criteria for the given exercise by title ascending.
     * @param exercise which has a list of grading criteria.
     * @private
     */
    private static sortedGradingCriteria(exercise: Exercise): GradingCriterion[] {
        return (
            exercise.gradingCriteria?.sort((crit1, crit2) => {
                if (crit1.title < crit2.title) {
                    return -1;
                } else if (crit1.title > crit2.title) {
                    return 1;
                } else {
                    return 0;
                }
            }) || []
        );
    }

    /**
     * Retrieves a list of test cases names contained in a result's feedback list
     * @param result
     * @private
     */
    private getTestCaseNamesFromResult(result: ResultWithPointsPerGradingCriterion): string[] {
        if (!result.result.feedbacks) {
            return [];
        }
        return result.result.feedbacks!.map((f) => {
            return f.text ? 'Test ' + f.text : 'Test ' + result.result.feedbacks?.indexOf(f) + 1;
        });
    }

    /**
     * Extracts test case results from a given result and returns them.
     * If no feedback is found in the result an empty array is returned
     * @param result from which the test case results should be extracted
     * @param testCaseNames list containing the test names
     * @private
     */
    private getTestCaseResults(result: ResultWithPointsPerGradingCriterion, testCaseNames: string[]): TestCaseResult[] {
        return (
            result.result.feedbacks?.map((f, i) => {
                let resultText;
                if (f.positive) {
                    resultText = 'Passed';
                } else {
                    resultText = f.detailText ? 'Failed: "' + f.detailText + '"' : 'Failed';
                }
                return { testName: testCaseNames[i], testResult: resultText } as TestCaseResult;
            }) ?? []
        );
    }
}

/**
 * A data row in the CSV file.
 *
 * For a list of all possible keys see {@link ExerciseScoresRowBuilder.keys}.
 */
type ExerciseScoresRow = any;

class ExerciseScoresRowBuilder {
    private readonly exercise: Exercise;
    private readonly gradingCriteria: GradingCriterion[];
    private readonly participation: StudentParticipation;
    private readonly resultWithPoints: ResultWithPointsPerGradingCriterion;
    private readonly testCaseResults?: TestCaseResult[];

    private csvRow: ExerciseScoresRow = {};

    constructor(
        exercise: Exercise,
        gradingCriteria: GradingCriterion[],
        participation: StudentParticipation,
        resultWithPoints: ResultWithPointsPerGradingCriterion,
        testCaseResults?: TestCaseResult[],
    ) {
        this.exercise = exercise;
        this.gradingCriteria = gradingCriteria;
        this.participation = participation;
        this.resultWithPoints = resultWithPoints;
        this.testCaseResults = testCaseResults;
    }

    /**
     * Builds the actual data row that should be exported as part of the CSV.
     */
    public build(): ExerciseScoresRow {
        this.setName();

        const score = roundValueSpecifiedByCourseSettings(this.resultWithPoints.result.score, getCourseFromExercise(this.exercise));
        this.set('Score', score);
        this.set('Points', this.resultWithPoints.totalPoints);

        this.setGradingCriteriaPoints();
        this.setProgrammingExerciseInformation();
        this.setTeamInformation();

        if (this.testCaseResults) {
            this.setTestCaseResults();
        }

        return this.csvRow;
    }

    /**
     * Stores the given value under the key in the row.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row. Replaced by the empty string if undefined.
     */
    private set<T>(key: string, value: T) {
        this.csvRow[key] = value ?? '';
    }

    /**
     * Sets the student or team name information in the row.
     * @private
     */
    private setName() {
        if (this.participation.team) {
            this.set('Team Name', this.participation.participantName);
            this.set('Team Short Name', this.participation.participantIdentifier);
        } else {
            this.set('Name', this.participation.participantName);
            this.set('Username', this.participation.participantIdentifier);
        }
    }

    /**
     * Sets the points for each grading criterion in the row.
     * @private
     */
    private setGradingCriteriaPoints() {
        let unnamedCriterionIndex = 1;
        this.gradingCriteria.forEach((criterion) => {
            const points = this.resultWithPoints.pointsPerCriterion.get(criterion.id!) || 0;
            if (criterion.title) {
                this.set(criterion.title, points);
            } else {
                this.set(`Unnamed Criterion ${unnamedCriterionIndex}`, points);
                unnamedCriterionIndex += 1;
            }
        });
    }

    /**
     * Adds information specific to programming exercises to the row.
     * @private
     */
    private setProgrammingExerciseInformation() {
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            const repoLink = (this.participation as ProgrammingExerciseStudentParticipation).repositoryUrl;
            this.set('Repo Link', repoLink);
        }
    }

    /**
     * Adds information specific to a team participation to the row.
     * @private
     */
    private setTeamInformation() {
        if (this.participation.team) {
            const students = `${this.participation.team?.students?.map((s) => s.name).join(', ')}`;
            this.set('Students', students);
        }
    }

    /**
     * Adds information about each exercise's test case result.
     * @private
     */
    private setTestCaseResults() {
        this.testCaseResults!.forEach((testResult) => {
            this.set(testResult.testName, testResult.testResult);
        });
    }

    /**
     * CSV columns [alternative column name for team exercises]:
     * - Name [Team Name]
     * - Username [Team Short Name]
     * - Score
     * - Points
     * - for each grading criterion `c` of the exercise: `c.title`
     *   (sorted by title, contains the total points given in this grading category)
     * - Repo Link (only for programming exercises)
     * - Students (only for team exercises; comma-separated list of students in the team)
     *
     * @param exercise The exercise for which results should be exported.
     * @param isTeamExercise True, if the students participate in teams in this exercise.
     * @param gradingCriteria The grading criteria that can be used in this exercise.
     * @param testCases Optional columns representing each exercise test case
     */
    public static keys(exercise: Exercise, isTeamExercise: boolean, gradingCriteria: GradingCriterion[], testCases?: string[]): Array<string> {
        const columns = [];

        if (isTeamExercise) {
            columns.push('Team Name', 'Team Short Name');
        } else {
            columns.push('Name', 'Username');
        }

        columns.push('Score', 'Points');

        let unnamedCriterionIndex = 1;
        gradingCriteria.forEach((criterion) => {
            if (criterion.title) {
                columns.push(criterion.title);
            } else {
                columns.push(`Unnamed Criterion ${unnamedCriterionIndex}`);
                unnamedCriterionIndex += 1;
            }
        });

        if (exercise.type === ExerciseType.PROGRAMMING) {
            columns.push('Repo Link');
        }

        if (isTeamExercise) {
            columns.push('Students');
        }

        if (testCases) {
            testCases.forEach((testCase) => {
                columns.push(testCase);
            });
        }

        return columns;
    }
}
