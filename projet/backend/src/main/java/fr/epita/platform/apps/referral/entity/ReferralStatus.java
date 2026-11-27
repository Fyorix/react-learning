package fr.epita.platform.apps.referral.entity;

import java.util.List;

public enum ReferralStatus {
    SUBMITTED, IN_REVIEW, HIRED, TRIAL_VALIDATED, BONUS_PAID, REJECTED, EXPIRED;

    /** Ordre d'avancement ; REJECTED depuis toute étape avant la validation de la période d'essai. */
    public static final List<ReferralStatus> FLOW = List.of(SUBMITTED, IN_REVIEW, HIRED, TRIAL_VALIDATED, BONUS_PAID);
}
