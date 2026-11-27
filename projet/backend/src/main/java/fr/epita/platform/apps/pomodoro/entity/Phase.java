package fr.epita.platform.apps.pomodoro.entity;

public enum Phase {
    IDLE, FOCUS, SHORT_BREAK, LONG_BREAK, PAUSED;

    public boolean running() {
        return this == FOCUS || this == SHORT_BREAK || this == LONG_BREAK;
    }
}
