package org.nanonative.ab.devconsole.model;

import org.nanonative.nano.helper.event.model.Event;

import java.time.Instant;

public class EventWrapper {

    private Event event;
    private Instant timestamp;

    private EventWrapper() {}

    public static Builder builder() {
        return new Builder();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Event getEvent() {
        return event;
    }

    public static final class Builder {
        private final EventWrapper instance = new EventWrapper();

        public Builder timestamp(Instant timestamp) {
            instance.timestamp = timestamp;
            return this;
        }

        public Builder event(Event event) {
            instance.event = event;
            return this;
        }

        public EventWrapper build() {
            return instance;
        }
    }
}
