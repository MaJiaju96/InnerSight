package cn.innersight.auth.service;

import cn.innersight.auth.repository.IdentityRepository;

import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!initialize")
class SessionCleanup {
    private final IdentityRepository repository;
    private final Clock clock;

    SessionCleanup(IdentityRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    @Scheduled(initialDelay = 300000, fixedDelay = 60000)
    void removeExpired() {
        long now = clock.instant().getEpochSecond();
        repository.cleanup(now);
    }
}
