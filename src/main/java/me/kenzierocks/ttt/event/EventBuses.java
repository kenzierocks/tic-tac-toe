package me.kenzierocks.ttt.event;

import com.google.common.eventbus.EventBus;

public final class EventBuses {
    
    public static final EventBus DEFAULT = new EventBus("default");

    private EventBuses() {
    }

}
