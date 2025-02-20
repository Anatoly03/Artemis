package de.tum.in.www1.artemis.service.metis;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

@Service
public class ConversationMessagingService extends PostingService {

    private final ConversationService conversationService;

    private final ConversationMessageRepository conversationMessageRepository;

    private final ChannelAuthorizationService channelAuthorizationService;

    protected ConversationMessagingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            ConversationMessageRepository conversationMessageRepository, AuthorizationCheckService authorizationCheckService, SimpMessageSendingOperations messagingTemplate,
            UserRepository userRepository, ConversationService conversationService, ConversationParticipantRepository conversationParticipantRepository,
            ChannelAuthorizationService channelAuthorizationService) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, messagingTemplate, conversationParticipantRepository);
        this.conversationService = conversationService;
        this.conversationMessageRepository = conversationMessageRepository;
        this.channelAuthorizationService = channelAuthorizationService;
    }

    /**
     * Creates a new message in a conversation
     *
     * @param courseId   the id where the conversation is located
     * @param newMessage the message to be created includes the conversation id
     * @return the created message
     */
    public Post createMessage(Long courseId, Post newMessage) {
        if (newMessage.getId() != null) {
            throw new BadRequestAlertException("A new message post cannot already have an ID", METIS_POST_ENTITY_NAME, "idexists");
        }
        if (newMessage.getConversation() == null || newMessage.getConversation().getId() == null) {
            throw new BadRequestAlertException("A new message post must have a conversation", METIS_POST_ENTITY_NAME, "conversationnotset");
        }

        var author = this.userRepository.getUserWithGroupsAndAuthorities();
        var course = preCheckUserAndCourse(author, courseId);
        newMessage.setAuthor(author);
        newMessage.setDisplayPriority(DisplayPriority.NONE);

        var conversation = conversationService.mayInteractWithConversationElseThrow(newMessage.getConversation().getId(), author);
        // extra checks for channels
        if (conversation instanceof Channel channel) {
            channelAuthorizationService.isAllowedToCreateNewPostInChannel(channel, author);
        }

        // update last message date of conversation
        conversation.setLastMessageDate(ZonedDateTime.now());
        conversation = conversationService.updateConversation(conversation);
        // update last read date and unread message count of author
        var authorParticipant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserIdElseThrow(conversation.getId(), author.getId());
        authorParticipant.setLastRead(ZonedDateTime.now());
        authorParticipant.setUnreadMessagesCount(0L);
        conversationParticipantRepository.save(authorParticipant);

        var createdMessage = conversationMessageRepository.save(newMessage);
        broadcastForPost(new PostDTO(createdMessage, MetisCrudAction.CREATE), course);

        if (conversation instanceof OneToOneChat) {
            var getNumberOfPosts = conversationMessageRepository.countByConversationId(conversation.getId());
            if (getNumberOfPosts == 1) { // first message in one to one chat --> notify all participants that a conversation with them has been created
                var participants = conversationParticipantRepository.findConversationParticipantByConversationId(conversation.getId()).stream()
                        .map(ConversationParticipant::getUser).filter(Objects::nonNull).collect(Collectors.toSet());
                conversationService.broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, conversation, participants);
            }
        }
        conversationParticipantRepository.incrementUnreadMessagesCountOfParticipants(conversation.getId(), author.getId());
        // ToDo: Optimization Idea: Maybe we can save this websocket call and instead get the last message date from the conversation object in the post somehow?
        // send conversation with updated last message date to participants. This is necessary to show the unread messages badge in the client
        conversationService.notifyAllConversationMembersAboutNewMessage(conversation);
        return createdMessage;
    }

    /**
     * fetch posts from database by conversationId
     *
     * @param pageable          requested page and page size
     * @param postContextFilter request object to fetch posts
     * @return page of posts that match the given context
     */
    public Page<Post> getMessages(Pageable pageable, @Valid PostContextFilter postContextFilter) {
        Page<Post> conversationPosts;
        if (postContextFilter.getConversationId() != null) {
            var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
            var conversation = conversationService.mayInteractWithConversationElseThrow(postContextFilter.getConversationId(), requestingUser);
            conversationPosts = conversationMessageRepository.findMessages(postContextFilter, pageable);
            // protect sample solution, grading instructions, etc.
            conversationPosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);
            setAuthorRoleOfPostings(conversationPosts.getContent());

            var participantOfRequestingUser = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserIdElseThrow(conversation.getId(),
                    requestingUser.getId());
            participantOfRequestingUser.setLastRead(ZonedDateTime.now());
            participantOfRequestingUser.setUnreadMessagesCount(0L);
            conversationParticipantRepository.save(participantOfRequestingUser);
        }
        else {
            throw new BadRequestAlertException("A new message post cannot be associated with more than one context", METIS_POST_ENTITY_NAME, "ambiguousContext");
        }
        return conversationPosts;
    }

    /**
     * Checks course, user and post validity,
     * updates non-restricted field of the post, persists the post,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId    id of the course the post belongs to
     * @param postId      id of the post to update
     * @param messagePost post to update
     * @return updated post that was persisted
     */
    public Post updateMessage(Long courseId, Long postId, Post messagePost) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        // check
        if (messagePost.getId() == null || !Objects.equals(messagePost.getId(), postId)) {
            throw new BadRequestAlertException("Invalid id", METIS_POST_ENTITY_NAME, "idnull");
        }
        final Course course = preCheckUserAndCourse(user, courseId);

        Post existingMessage = conversationMessageRepository.findMessagePostByIdElseThrow(postId);
        Conversation conversation = mayUpdateOrDeleteMessageElseThrow(existingMessage, user);

        // ToDo: find a cleaner way to do this instead of making the string here in the server

        var nameOfEditor = user.getName();
        // use login as fallback
        if (nameOfEditor == null || nameOfEditor.isBlank()) {
            nameOfEditor = user.getLogin();
        }
        var editedByText = "(edited by " + nameOfEditor + " on " + ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) + " [UTC])";
        messagePost.setContent(messagePost.getContent() + "\n" + editedByText);

        // update: allow overwriting of values only for depicted fields
        existingMessage.setContent(messagePost.getContent());

        Post updatedPost = conversationMessageRepository.save(existingMessage);
        updatedPost.setConversation(conversation);

        // emit a post update via websocket
        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course);

        return updatedPost;
    }

    /**
     * Checks course, user and post validity,
     * determines authority to delete post and deletes the post
     *
     * @param courseId id of the course the post belongs to
     * @param postId   id of the message post to delete
     */
    public void deleteMessageById(Long courseId, Long postId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        final Course course = preCheckUserAndCourse(user, courseId);
        Post post = conversationMessageRepository.findMessagePostByIdElseThrow(postId);
        var conversation = mayUpdateOrDeleteMessageElseThrow(post, user);
        post.setConversation(conversation);

        // delete
        conversationMessageRepository.deleteById(postId);
        conversationParticipantRepository.decrementUnreadMessagesCountOfParticipants(conversation.getId(), user.getId());
        conversation = conversationService.getConversationById(conversation.getId());

        conversationService.notifyAllConversationMembersAboutUpdate(conversation);

        broadcastForPost(new PostDTO(post, MetisCrudAction.DELETE), course);
    }

    private Conversation mayUpdateOrDeleteMessageElseThrow(Post existingMessagePost, User user) {
        if (existingMessagePost.getConversation() == null) {
            throw new BadRequestAlertException("The post does not belong to a conversation", METIS_POST_ENTITY_NAME, "conversationnotset");
        }

        var conversation = conversationService.getConversationById(existingMessagePost.getConversation().getId());
        if (existingMessagePost.getAuthor().getId().equals(user.getId())
                || (conversation instanceof Channel channel && channelAuthorizationService.isAllowedToEditOrDeleteMessagesOfOtherUsers(channel, user))) {
            if (conversation instanceof Channel channel && channel.getIsArchived()) {
                throw new BadRequestAlertException("A message cannot be created in an archived channel", METIS_POST_ENTITY_NAME, "channelarchived");
            }
            return conversation;
        }
        else {
            throw new AccessForbiddenException("You are not allowed to edit or delete this message");
        }
    }

    @Override
    String getEntityName() {
        return METIS_POST_ENTITY_NAME;
    }
}
