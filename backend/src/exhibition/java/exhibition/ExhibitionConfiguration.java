package exhibition;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.kkirikkiri.user.service.UserService;
import com.kkirikkiri.survey.service.*;
import com.kkirikkiri.community.post.service.*;
import com.kkirikkiri.community.post.repository.CommunityPostReadRepository;
import com.kkirikkiri.community.comment.service.*;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.calendar.service.CalendarService;
import com.kkirikkiri.matching.service.*;
import com.kkirikkiri.report.service.ReportService;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.user.repository.AdminMemberQueryRepository;
import com.kkirikkiri.community.repository.*;
import com.kkirikkiri.report.repository.AdminReportQueryRepository;
import com.kkirikkiri.calendar.repository.AdminCrawledScheduleQueryRepository;

/** No component scan: no controller, server, mail, crawler, runner, async listener or scheduler. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan("com.kkirikkiri")
@EnableJpaRepositories("com.kkirikkiri")
@Import({UserService.class, ProfileSurveyService.class, RoommatePreferenceService.class,
        CommunityPostService.class, CommunityPostReadRepository.class, CommunityCommentService.class,
        CommunityCommentWriteService.class, CommunityAnonymousAuthorService.class, PostImageStorage.class,
        PostImageCleanupService.class, PostImageCleanupWorker.class, ChatService.class, CalendarService.class,
        RoommateMatchCalculator.class, MatchingRefreshService.class, RoommateMatchingService.class,
        com.kkirikkiri.matching.repository.MatchingInputRepository.class,
        ReportService.class, AdminService.class, AdminMemberQueryRepository.class, AdminPostQueryRepository.class,
        AdminCommentQueryRepository.class, AdminReportQueryRepository.class, AdminCrawledScheduleQueryRepository.class,
        ExhibitionWriter.class})
public class ExhibitionConfiguration {
    @Bean PasswordEncoder exhibitionPasswordEncoder() { return new BCryptPasswordEncoder(); }
}
