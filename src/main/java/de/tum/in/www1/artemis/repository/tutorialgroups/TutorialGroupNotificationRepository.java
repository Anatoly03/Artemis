package de.tum.in.www1.artemis.repository.tutorialgroups;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.TutorialGroupNotification;
import jakarta.transaction.Transactional;

@Repository
public interface TutorialGroupNotificationRepository extends JpaRepository<TutorialGroupNotification, Long> {

    @Transactional // ok because of modifying query
    @Modifying
    void deleteAllByTutorialGroupId(Long tutorialGroupId);
}
