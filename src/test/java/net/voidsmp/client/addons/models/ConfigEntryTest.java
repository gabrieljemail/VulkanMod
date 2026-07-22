package net.voidsmp.client.addons.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ConfigEntryTest {

    @Test
    void addListenerDoesNotFireImmediately() {
        ConfigEntry<Integer> entry = new ConfigEntry<>("x", ConfigEntryType.INT, 0);
        int[] callCount = {0};

        entry.addListener(v -> callCount[0]++);

        assertEquals(0, callCount[0], "addListener should not fire on registration, unlike onChange");
    }

    @Test
    void addListenerFiresOnSubsequentChanges() {
        ConfigEntry<Integer> entry = new ConfigEntry<>("x", ConfigEntryType.INT, 0);
        int[] lastValue = {-1};

        entry.addListener(v -> lastValue[0] = v);
        entry.set(5);

        assertEquals(5, lastValue[0]);
    }

    @Test
    void closingTheSubscriptionStopsFutureNotifications() {
        ConfigEntry<Integer> entry = new ConfigEntry<>("x", ConfigEntryType.INT, 0);
        int[] callCount = {0};
        AutoCloseable subscription = entry.addListener(v -> callCount[0]++);

        entry.set(1);
        assertEquals(1, callCount[0]);

        try {
            subscription.close();
        } catch (Exception e) {
            fail(e);
        }

        entry.set(2);
        assertEquals(1, callCount[0], "listener should not fire after being removed");
    }
}
