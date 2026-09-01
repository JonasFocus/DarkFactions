package com.darkfactions.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class FactionPlayerTest {

    @Test
    void setFactionsCreatedMarksDirty() {
        FactionPlayer player = new FactionPlayer(UUID.randomUUID());
        player.clearDirty();

        player.setFactionsCreated(1);

        assertEquals(1, player.getFactionsCreated());
        assertTrue(player.isDirty());
    }
}
