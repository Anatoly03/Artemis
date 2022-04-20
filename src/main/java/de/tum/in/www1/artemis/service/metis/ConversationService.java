package de.tum.in.www1.artemis.service.metis;

import java.util.List;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

@Service
public class ConversationService {

    private static final String CONVERSATION_ENTITY_NAME = "messages.conversation";

    private static final String CONVERSATION_DETAILS_ENTITY_NAME = "messages.conversationParticipant";

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ConversationRepository conversationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public ConversationService(UserRepository userRepository, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            ConversationParticipantRepository conversationParticipantRepository, ConversationRepository conversationRepository, SimpMessageSendingOperations messagingTemplate) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.conversationRepository = conversationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Persists given conversation
     *
     * @param courseId      id of course the conversation belongs to
     * @param conversation  conversation to be persisted
     * @return              persisted conversation
     */
    public Conversation createConversation(Long courseId, Conversation conversation) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        if (conversation.getId() != null) {
            throw new BadRequestAlertException("A new conversation cannot already have an ID", CONVERSATION_ENTITY_NAME, "idexists");
        }

        if (conversation.getConversationParticipants().isEmpty()
                || conversation.getConversationParticipants().stream().anyMatch(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId()))) {
            throw new BadRequestAlertException("A new conversation must have other conversation participants", CONVERSATION_DETAILS_ENTITY_NAME, "invalidconversationparticipants");
        }

        final Course course = checkUserAndCourse(user, courseId);
        conversation.setCourse(course);

        Conversation savedConversation = conversationRepository.save(conversation);

        ConversationParticipant conversationParticipantOfCurrentUser = new ConversationParticipant();
        conversationParticipantOfCurrentUser.setUser(user);
        conversation.getConversationParticipants().add(conversationParticipantOfCurrentUser);

        conversation.getConversationParticipants().forEach(conversationParticipant -> conversationParticipantToCreate(conversationParticipant, savedConversation));
        conversationParticipantRepository.saveAll(conversation.getConversationParticipants());
        savedConversation.setConversationParticipants(conversation.getConversationParticipants());

        // informs involved users about a new conversation
        broadcastForConversation(new ConversationDTO(savedConversation, MetisCrudAction.CREATE));

        return savedConversation;
    }

    /**
     * fetch conversation from database by conversationId
     *
     * @param conversationId  id of the conversation to fetch
     * @return  fetched conversation
     */
    public Conversation getConversationById(Long conversationId) {
        return conversationRepository.findConversationByIdWithConversationParticipants(conversationId);
    }

    /**
     * fetch conversations from database by userId and courseId
     *
     * @param courseId  id of course the conversations belongs to
     * @return          fetched conversations
     */
    public List<Conversation> getConversationsOfUser(Long courseId) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        List<Conversation> conversations = conversationRepository.findConversationsOfUserWithConversationParticipants(courseId, user.getId());

        conversations.forEach(conversation -> {
            conversation.getConversationParticipants().forEach(conversationParticipant -> {
                if (!conversationParticipant.getUser().getId().equals(user.getId())) {
                    conversationParticipant.filterSensitiveInformation();
                }
            });
        });

        return conversations;
    }

    /**
     * Broadcasts a conversation event in a course under a specific topic via websockets
     *
     * @param conversationDTO object including the affected conversation as well as the action
     */
    private void broadcastForConversation(ConversationDTO conversationDTO) {
        String courseTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + conversationDTO.getConversation().getCourse().getId();
        String conversationParticipantTopicName = courseTopicName + "/conversations/user/";

        conversationDTO.getConversation().getConversationParticipants().forEach(
                conversationParticipant -> messagingTemplate.convertAndSend(conversationParticipantTopicName + conversationParticipant.getUser().getId(), conversationDTO));
    }

    void mayInteractWithConversationElseThrow(Long conversationId, User user) {
        // use object fetched from database
        Conversation conversation = conversationRepository.findConversationByIdWithConversationParticipants(conversationId);
        if (conversation == null
                || conversation.getConversationParticipants().stream().noneMatch(conversationParticipant -> conversationParticipant.getUser().getId().equals(user.getId()))) {
            throw new AccessForbiddenException("User not allowed to access this conversation!");
        }
    }

    /**
     * Helper method that prepares a conversationParticipant that will later be persisted
     * @param conversationParticipant   conversationParticipant to be created
     * @param conversation              conversation in association with the conversationParticipant
     * @return                          returned conversationParticipant ready to be persisted
     */
    private ConversationParticipant conversationParticipantToCreate(ConversationParticipant conversationParticipant, Conversation conversation) {
        conversationParticipant.setConversation(conversation);
        conversationParticipant.setClosed(false);
        conversationParticipant.setLastRead(conversation.getLastMessageDate());
        return conversationParticipant;
    }

    Course checkUserAndCourse(User user, Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        // user has to at least have student role in the course
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        return course;
    }

    /**
     * Retrieve the entity name used in ResponseEntity
     *
     * @return conversation entity name
     */
    public String getEntityName() {
        return CONVERSATION_ENTITY_NAME;
    }
}
