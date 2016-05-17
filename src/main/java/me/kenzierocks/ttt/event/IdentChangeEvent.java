package me.kenzierocks.ttt.event;

import java.util.UUID;

public class IdentChangeEvent implements Event {

    public final UUID newId;

    public IdentChangeEvent(UUID newId) {
        this.newId = newId;
    }

}
