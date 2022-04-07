package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.repository.metis.ConversationRepository;
import de.tum.in.www1.artemis.service.metis.ConversationService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationDTO;

class ConversationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationService conversationService;

    private Conversation existingConversation;

    private Course course;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(5, 5, 0, 1);

        course = database.createCourse(1L);

        existingConversation = database.createConversation(course);

        SimpMessageSendingOperations messagingTemplate = mock(SimpMessageSendingOperations.class);
        doNothing().when(messagingTemplate).convertAndSend(any());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    // Conversation

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateConversation() throws Exception {
        Conversation conversationToSave = createConversation(course, database);

        Conversation createdConversation = request.postWithResponseBody("/api/courses/" + course.getId() + "/conversations/", conversationToSave, Conversation.class,
                HttpStatus.CREATED);

        checkCreatedConversationParticipants(createdConversation.getConversationParticipants());
        checkCreatedConversation(conversationToSave, createdConversation);

        assertThat(conversationRepository.findById(createdConversation.getId())).isNotEmpty();

        // checks if members of the created conversation were notified via broadcast
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(ConversationDTO.class));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateConversation_badRequest() throws Exception {
        Conversation conversationToSave = new Conversation();

        // conversation without required conversationParticipant
        createConversationBadRequest(conversationToSave);

        conversationToSave = createConversation(course, database);
        conversationToSave.setId(1L);

        // conversation with existing ID
        createConversationBadRequest(conversationToSave);

        // conversation with user's own conversationParticipant object
        conversationToSave = createConversation(course, database);
        ConversationParticipant conversationParticipant = new ConversationParticipant();
        conversationParticipant.setUser(database.getUserByLogin("student1"));
        conversationToSave.getConversationParticipants().add(conversationParticipant);
        createConversationBadRequest(conversationToSave);

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetConversationParticipantByUserId() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        List<Conversation> conversationsOfUser;

        conversationsOfUser = request.getList("/api/courses/" + course.getId() + "/conversations/", HttpStatus.OK, Conversation.class, params);
        assertThat(conversationsOfUser.get(0)).isEqualTo(existingConversation);

        database.changeUser("student2");
        conversationsOfUser = request.getList("/api/courses/" + course.getId() + "/conversations/", HttpStatus.OK, Conversation.class, params);
        assertThat(conversationsOfUser.get(0)).isEqualTo(existingConversation);

        database.changeUser("student3");
        conversationsOfUser = request.getList("/api/courses/" + course.getId() + "/conversations/", HttpStatus.OK, Conversation.class, params);
        assertThat(conversationsOfUser).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetConversationById() {
        Conversation conversation = conversationService.getConversationById(existingConversation.getId());
        assertThat(conversation).isEqualTo(existingConversation);
    }

    private void createConversationBadRequest(Conversation conversationToSave) throws Exception {
        Conversation createdConversation = request.postWithResponseBody("/api/courses/" + course.getId() + "/conversations/", conversationToSave, Conversation.class,
                HttpStatus.BAD_REQUEST);
        assertThat(createdConversation).isNull();

        // checks if members of the created conversation were not notified via broadcast
        verify(messagingTemplate, times(0)).convertAndSend(anyString(), any(ConversationDTO.class));
    }

    static Conversation createConversation(Course course, DatabaseUtilService databaseUtilService) {
        Conversation conversation = new Conversation();

        ConversationParticipant conversationParticipant2 = new ConversationParticipant();
        conversationParticipant2.setUser(databaseUtilService.getUserByLogin("student2"));
        conversationParticipant2.setLastRead(conversation.getCreationDate());

        conversation.getConversationParticipants().add(conversationParticipant2);
        conversation.setCourse(course);

        return conversation;
    }

    private void checkCreatedConversation(Conversation expectedConversation, Conversation createdConversation) {
        assertThat(createdConversation).isNotNull();
        assertThat(createdConversation.getId()).isNotNull();
        assertThat(createdConversation.getCreationDate()).isNotNull();
        assertThat(createdConversation.getLastMessageDate()).isNotNull();

        assertThat(createdConversation.getCourse()).isEqualTo(expectedConversation.getCourse());
        assertThat(createdConversation.getCreationDate()).isNotNull();
    }

    private void checkCreatedConversationParticipants(Set<ConversationParticipant> conversationParticipants) {
        // check each individual conversationParticipant
        conversationParticipants.forEach(conversationParticipant -> {
            assertThat(conversationParticipant.isClosed()).isFalse();
            assertThat(conversationParticipant.getUser()).isNotNull();
            assertThat(conversationParticipant.getLastRead()).isNotNull();
        });
    }
}
