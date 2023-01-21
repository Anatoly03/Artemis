package de.tum.in.www1.artemis.config;

import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import de.tum.in.www1.artemis.security.localVC.*;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCService;

/**
 * Configuration of the JGit Servlet that handles fetch and push requests for local Version Control.
 */
@Configuration
@Profile("localvc")
public class JGitServletConfiguration {

    private final Logger log = LoggerFactory.getLogger(JGitServletConfiguration.class);

    private final LocalVCServletService localVCServletService;

    private final LocalVCFilterService localVCFilterService;

    private final LocalVCService localVCService;

    public JGitServletConfiguration(LocalVCServletService localVCServletService, LocalVCFilterService localVCFilterService, LocalVCService localVCService) {
        this.localVCServletService = localVCServletService;
        this.localVCFilterService = localVCFilterService;
        this.localVCService = localVCService;
    }

    /**
     * @return GitServlet (Git server implementation by JGit) configured with a repository resolver and filters for fetch and push requests.
     */
    @Bean
    public ServletRegistrationBean<GitServlet> jgitServlet() {

        try {
            GitServlet gitServlet = new GitServlet();
            gitServlet.setRepositoryResolver((req, name) -> {
                // req – the current request, may be used to inspect session state including cookies or user authentication.
                // name – name of the repository, as parsed out of the URL (everything after /git).

                // Return the opened repository instance.
                return localVCServletService.resolveRepository(name);
            });

            // Add filters that every request to the JGit Servlet goes through, one for each fetch request, and one for each push request.
            gitServlet.addUploadPackFilter(new LocalVCFetchFilter(localVCFilterService));
            gitServlet.addReceivePackFilter(new LocalVCPushFilter(localVCFilterService));

            gitServlet.setReceivePackFactory((req, db) -> {
                ReceivePack receivePack = new ReceivePack(db);
                // Add a hook that triggers the creation of a new submission after the push went through successfully.
                receivePack.setPostReceiveHook(new LocalVCPostPushHook(localVCService));
                return receivePack;
            });

            log.info("Registering GitServlet");
            return new ServletRegistrationBean<>(gitServlet, "/git/*");
        }
        catch (Exception e) {
            log.error("Something went wrong creating the JGit Servlet.");
        }
        return null;
    }

}