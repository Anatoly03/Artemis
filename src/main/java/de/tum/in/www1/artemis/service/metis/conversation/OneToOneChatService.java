package de.tum.in.www1.artemis.service.metis.conversation;

import java.time.ZonedDateTime;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;

@Service
public class OneToOneChatService {

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final OneToOneChatRepository oneToOneChatRepository;

    private final UserRepository userRepository;

    public OneToOneChatService(ConversationParticipantRepository conversationParticipantRepository, OneToOneChatRepository oneToOneChatRepository, UserRepository userRepository) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.oneToOneChatRepository = oneToOneChatRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a new OneToOneChat between two users
     * <p>
     * Note: if a OneToOneChat between the two users already exists, it will be returned instead of a new one
     *
     * @param course the course the OneToOneChat is in
     * @param userA  the first user
     * @param userB  the second user
     * @return the newly created OneToOneChat or the existing one
     */
    public OneToOneChat startOneToOneChat(Course course, User userA, User userB) {
        var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        var existingChatBetweenUsers = oneToOneChatRepository.findBetweenUsersWithParticipantsAndUserGroups(course.getId(), userA.getId(), userB.getId());
        if (existingChatBetweenUsers.isPresent()) {
            return existingChatBetweenUsers.get();
        }
        var oneToOneChat = new OneToOneChat();
        oneToOneChat.setCourse(course);
        oneToOneChat.setCreator(requestingUser);
        var savedChat = oneToOneChatRepository.save(oneToOneChat);

        ConversationParticipant participationOfUserA = createChatParticipant(userA, oneToOneChat);
        ConversationParticipant participationOfUserB = createChatParticipant(userB, oneToOneChat);
        conversationParticipantRepository.saveAll(List.of(participationOfUserA, participationOfUserB));
        return oneToOneChatRepository.save(savedChat);
    }

    @NotNull
    private ConversationParticipant createChatParticipant(User user, OneToOneChat oneToOneChat) {
        var participant = new ConversationParticipant();
        participant.setUser(user);
        participant.setConversation(oneToOneChat);
        participant.setIsModerator(false);
        participant.setIsHidden(false);
        participant.setIsFavorite(false);
        // set the last reading time of a participant in the past when creating conversation for the first time!
        participant.setLastRead(ZonedDateTime.now().minusYears(2));
        participant.setUnreadMessagesCount(0L);
        return participant;
    }
}
