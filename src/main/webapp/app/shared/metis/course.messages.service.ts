import { Injectable, OnDestroy } from '@angular/core';
import { map, Observable, ReplaySubject } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ConversationService } from 'app/shared/metis/conversation.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation-dto.model';
import { MetisPostAction, MetisWebsocketChannelPrefix } from 'app/shared/metis/metis.util';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

@Injectable()
export class CourseMessagesService implements OnDestroy {
    private conversations$: ReplaySubject<Conversation[]> = new ReplaySubject<Conversation[]>(1);
    private subscribedChannel?: string;
    userId: number;

    constructor(protected conversationService: ConversationService, private jhiWebsocketService: JhiWebsocketService, protected accountService: AccountService) {
        this.accountService.identity().then((user: User) => {
            this.userId = user.id!;
        });
    }

    ngOnDestroy(): void {
        if (this.subscribedChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscribedChannel);
        }
    }

    /**
     * creates a new conversation by invoking the conversation service
     * @param {number} courseId             course to associate the conversation to
     * @param {Conversation} conversation    to be created
     * @return {Observable<Conversation>}    created conversation
     */
    createConversation(courseId: number, conversation: Conversation): Observable<Conversation> {
        return this.conversationService.create(courseId, conversation).pipe(map((res: HttpResponse<Conversation>) => res.body!));
    }

    get conversations(): Observable<Conversation[]> {
        return this.conversations$.asObservable();
    }

    getConversationsOfUser(courseId: number): void {
        this.conversationService.getConversationsOfUser(courseId).subscribe((res) => {
            this.conversations$.next(res.body!);
            this.createSubscription(courseId, this.userId);
        });
    }

    private createSubscription(courseId: number, userId: number) {
        const channel = this.channelName(courseId, userId);

        this.jhiWebsocketService.unsubscribe(channel);
        this.subscribedChannel = channel;
        this.jhiWebsocketService.subscribe(channel);

        this.jhiWebsocketService.receive(channel).subscribe((conversationDTO: ConversationDTO) => {
            if (conversationDTO.crudAction === MetisPostAction.CREATE) {
                this.getConversationsOfUser(courseId);
            }
        });
    }

    private channelName(courseId: number, userId: number) {
        const courseTopicName = MetisWebsocketChannelPrefix + 'courses/' + courseId;
        const channel = courseTopicName + '/conversations/user/' + userId;
        return channel;
    }
}