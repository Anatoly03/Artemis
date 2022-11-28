import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FeedbackCollapseComponent } from 'app/exercises/shared/feedback/feedback-collapse.component';
import { BarChartModule } from '@swimlane/ngx-charts';
import { FeedbackListComponent } from 'app/exercises/shared/feedback/feedback-list.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseActionsModule, ArtemisSharedComponentModule, BarChartModule],
    declarations: [FeedbackCollapseComponent, FeedbackListComponent],
    exports: [FeedbackCollapseComponent, FeedbackListComponent],
})
export class ArtemisFeedbackModule {}
