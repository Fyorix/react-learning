package fr.epita.platform.apps.idea.entity;

import java.util.Set;

/** NEW → UNDER_REVIEW → ACCEPTED / REJECTED ; ACCEPTED → DONE. */
public enum IdeaStatus {
    NEW, UNDER_REVIEW, ACCEPTED, REJECTED, DONE;

    public boolean canMoveTo(IdeaStatus next) {
        return switch (this) {
            case NEW -> next == UNDER_REVIEW;
            case UNDER_REVIEW -> Set.of(ACCEPTED, REJECTED).contains(next);
            case ACCEPTED -> next == DONE;
            case REJECTED, DONE -> false;
        };
    }
}
