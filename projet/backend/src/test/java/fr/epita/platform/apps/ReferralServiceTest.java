package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.referral.dto.ReferralDtos.*;
import fr.epita.platform.apps.referral.entity.ReferralStatus;
import fr.epita.platform.apps.referral.service.ReferralService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferralServiceTest extends IntegrationTest {

    @Autowired
    ReferralService service;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser admin;
    AuthUser alice;
    AuthUser bob;
    JobDto job;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        job = service.createJob(admin, new JobRequest("Dev Java", "Plateforme", "Spring", new BigDecimal("1500.00"), true));
    }

    private MyReferralDto refer(AuthUser who, String email) {
        return service.refer(who, new ReferralRequest(job.id(), "Karim Diallo", email, null, "Excellent collègue"));
    }

    private void step(MyReferralDto r, ReferralStatus s) {
        service.changeStatus(admin, r.id(), new StatusRequest(s, "note RH"));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void selfReferralAndDuplicatesAreRefused() {
        assertCode(() -> refer(alice, alice.email().toUpperCase()), "SELF_REFERRAL");
        refer(alice, "karim@example.com");
        assertCode(() -> refer(bob, "KARIM@example.com"), "ALREADY_REFERRED");
        service.updateJob(admin, job.id(), new JobRequest("Dev Java", "Plateforme", "Spring", new BigDecimal("1500.00"), false));
        assertCode(() -> refer(bob, "autre@example.com"), "JOB_CLOSED");
    }

    @Test
    void statusMovesOneStepAtATimeAndBonusNeedsValidatedTrial() {
        MyReferralDto r = refer(alice, "karim@example.com");
        assertCode(() -> step(r, ReferralStatus.HIRED), "INVALID_STATUS_TRANSITION");
        step(r, ReferralStatus.IN_REVIEW);
        step(r, ReferralStatus.HIRED);
        assertCode(() -> service.bonusPaid(admin, r.id()), "TRIAL_NOT_VALIDATED");
        assertThat(service.bonuses(admin, "due")).isEmpty();
        step(r, ReferralStatus.TRIAL_VALIDATED);
        assertThat(service.bonuses(admin, "due")).hasSize(1);
        assertThat(service.bonusPaid(admin, r.id()).status()).isEqualTo(ReferralStatus.BONUS_PAID);
        assertCode(() -> service.bonusPaid(admin, r.id()), "INVALID_STATUS_TRANSITION");
        assertThat(service.adminList(admin, null, null).get(0).internalComment()).isEqualTo("note RH");
    }

    @Test
    void inactiveReferrerGetsNoBonus() {
        MyReferralDto r = refer(alice, "karim@example.com");
        step(r, ReferralStatus.IN_REVIEW);
        step(r, ReferralStatus.HIRED);
        step(r, ReferralStatus.TRIAL_VALIDATED);
        jdbc.update("update users set enabled = false where id = ?", alice.id());
        assertThat(service.bonuses(admin, "due")).isEmpty();
        assertCode(() -> service.bonusPaid(admin, r.id()), "REFERRER_INACTIVE");
    }

    @Test
    void staleReferralsExpireAfterSixtyDays() {
        MyReferralDto r = refer(alice, "karim@example.com");
        jdbc.update("update referrals set status_changed_at = now() - interval '61 days' where id = ?", r.id());
        assertThat(service.mine(alice).get(0).status()).isEqualTo(ReferralStatus.EXPIRED);
        assertCode(() -> step(r, ReferralStatus.IN_REVIEW), "INVALID_STATUS_TRANSITION");
        assertCode(() -> service.adminList(alice, null, null), "FORBIDDEN");
    }
}
