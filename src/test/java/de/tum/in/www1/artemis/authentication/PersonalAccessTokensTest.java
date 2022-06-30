package de.tum.in.www1.artemis.authentication;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;

@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "pat" })
public class PersonalAccessTokensTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TOKEN_API_URL = "/api/personal-access-token";

    private static final String ACTIVATE_NAME = "student1";

    private static final String NOT_ACTIVATE_NAME = "student2";

    @Value("${artemis.personal-access-token.max-lifetime}")
    private long personalAccessTokenMaxLifetimeMilliseconds;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    public void setUp() {
        database.addUsers(2, 0, 0, 0);

        User notActivated = userRepository.findOneWithGroupsAndAuthoritiesByLogin(NOT_ACTIVATE_NAME).get();
        notActivated.setActivated(false);
        userRepository.save(notActivated);
    }

    @AfterEach
    public void teardown() {
        database.resetDatabase();
    }

    @Test
    @WithAnonymousUser
    public void testNotFullyAuthorized() throws Exception {
        request.post(TOKEN_API_URL, this.personalAccessTokenMaxLifetimeMilliseconds, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = NOT_ACTIVATE_NAME, roles = "ADMIN")
    public void testNotActivated() throws Exception {
        request.post(TOKEN_API_URL, this.personalAccessTokenMaxLifetimeMilliseconds, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = ACTIVATE_NAME, roles = "ADMIN")
    public void testFullyAuthorized() throws Exception {
        request.postWithoutLocation(TOKEN_API_URL, this.personalAccessTokenMaxLifetimeMilliseconds, HttpStatus.OK, null);
    }

    @Test
    @WithMockUser(username = ACTIVATE_NAME, roles = "ADMIN")
    public void testInvalidLifetime() throws Exception {
        request.post(TOKEN_API_URL, this.personalAccessTokenMaxLifetimeMilliseconds + 1, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = ACTIVATE_NAME, roles = "ADMIN")
    public void testNoLifetime() throws Exception {
        request.post(TOKEN_API_URL, "", HttpStatus.BAD_REQUEST);
    }
}
