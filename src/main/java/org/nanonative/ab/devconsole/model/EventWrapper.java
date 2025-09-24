package org.nanonative.ab.devconsole.model;

import org.nanonative.nano.helper.event.model.Event;

import java.time.Instant;

public record EventWrapper(Event<?, ?> event, Instant timestamp){}
