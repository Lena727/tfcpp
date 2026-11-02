package tfcpp.pneumatics.gas;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GasChunkDataTest
{
    @Test
    void aNegativeAmountThatWasSavedIsCleanedWhenTheChunkLoads()
    {
        final GasChunkData stored = new GasChunkData();
        final int key = GasChunkData.key(3, 5, 7);
        final float[] cell = stored.getOrCreate(key);
        cell[Gas.FLUE_NITROGEN.ordinal()] = -4e-8f; // What the burning of a sulfide and ammonia used to leave behind
        cell[Gas.CARBON_DIOXIDE.ordinal()] = 2.5f;
        cell[Gas.AMMONIA.ordinal()] = Float.NaN;
        stored.addDeposit(Gas.ARSENIC_TRIOXIDE, -1f);

        final CompoundTag tag = stored.save();
        final GasChunkData loaded = new GasChunkData();
        loaded.load(tag);

        final float[] after = loaded.get(key);
        assertNotNull(after);
        assertEquals(0f, after[Gas.FLUE_NITROGEN.ordinal()], "A negative amount is nothing");
        assertEquals(0f, after[Gas.AMMONIA.ordinal()], "So is a number that is not one");
        assertEquals(2.5f, after[Gas.CARBON_DIOXIDE.ordinal()], 1e-6f, "Everything else stays as it was");
        assertEquals(0f, loaded.deposit(Gas.ARSENIC_TRIOXIDE), "A deposit cannot be below nothing either");
    }
}
