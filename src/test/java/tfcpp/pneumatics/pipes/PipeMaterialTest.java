package tfcpp.pneumatics.pipes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** What iron, lead and stoneware bodies hold and what eats them: the numbers behind the fittings. */
class PipeMaterialTest
{
    private static final float ROOM = PipePhysics.ROOM_TEMPERATURE;
    private static final float HOT = 373f;
    private static final String[] STRONG_ACIDS = {"tfcpp:sulfuric_acid", "tfcpp:chamber_acid", "tfcpp:nitric_acid", "tfcpp:hydrochloric_acid"};

    @Test
    void stoneware_resists_every_strong_acid_at_any_heat()
    {
        for (String acid : STRONG_ACIDS)
        {
            assertEquals(0f, PipeMaterial.CERAMIC.corrosion(acid, ROOM), 0f, acid + " cold");
            assertEquals(0f, PipeMaterial.CERAMIC.corrosion(acid, HOT), 0f, acid + " hot");
            assertEquals(0f, PipeMaterial.CERAMIC.corrosion(acid, 500f), 0f, acid + " at 227 C");
        }
    }

    @Test
    void stoneware_is_brittle_and_holds_only_a_few_atmospheres()
    {
        final float rating = PipeMaterial.CERAMIC.rating(1f, ROOM);
        assertTrue(rating > 2f && rating < 5f, "Chemical stoneware pipe is good for 2 to 4 bar: " + rating);
        assertTrue(rating < PipeMaterial.LEAD.rating(1f, ROOM), "Lead is the stronger of the two");
        assertTrue(PipeMaterial.LEAD.rating(1f, ROOM) < PipeMaterial.WROUGHT_IRON.rating(1f, ROOM), "And iron the strongest");
        assertTrue(PipeMaterial.CERAMIC.rating(0.5f, ROOM) < rating, "A worn wall holds less");
    }

    @Test
    void hot_strong_alkali_takes_the_glaze_off_stoneware_and_cold_does_not()
    {
        assertEquals(0f, PipeMaterial.CERAMIC.corrosion("tfc:lye", ROOM), 0f);
        assertTrue(PipeMaterial.CERAMIC.corrosion("tfc:lye", HOT) > 0f);
        assertEquals(0f, PipeMaterial.LEAD.corrosion("tfc:lye", HOT), 0f, "Lead is nothing to lye at this level of detail");
    }

    @Test
    void lead_holds_chamber_acid_cool_and_loses_it_above_eighty_degrees()
    {
        final float cool = PipeMaterial.LEAD.corrosion("tfcpp:chamber_acid", ROOM);
        final float hot = PipeMaterial.LEAD.corrosion("tfcpp:chamber_acid", HOT);
        assertTrue(cool > 0f && cool < PipeMaterial.WROUGHT_IRON.corrosion("tfcpp:chamber_acid", ROOM) / 100f, "Lead holds the cool acid far better than iron");
        assertEquals(cool * PipeMaterial.HOT_ACID_FACTOR, hot, 1e-6f, "Above 80 C the film is gone");
        assertEquals(cool, PipeMaterial.LEAD.corrosion("tfcpp:chamber_acid", PipeMaterial.HOT_ACID_KELVIN), 0f, "At 80 C itself it still holds");
    }

    @Test
    void nitric_acid_eats_lead_and_iron_in_dilute_acid_dies_fast()
    {
        assertTrue(PipeMaterial.LEAD.corrosion("tfcpp:nitric_acid", ROOM) >= 0.5f);
        assertTrue(PipeMaterial.WROUGHT_IRON.corrosion("tfcpp:chamber_acid", ROOM) >= 0.5f, "Iron in 65% acid loses half its wall in a day");
        assertEquals(PipeMaterial.WROUGHT_IRON.corrosion("tfcpp:hydrochloric_acid", ROOM), 2f, 0f, "Chloride keeps iron from passivating");
    }

    @Test
    void a_safety_valve_is_set_below_what_its_body_holds()
    {
        final float iron = PipeMaterial.WROUGHT_IRON.safetyValveSetting(8f);
        final float lead = PipeMaterial.LEAD.safetyValveSetting(8f);
        final float ceramic = PipeMaterial.CERAMIC.safetyValveSetting(8f);
        assertEquals(8f, iron, 1e-6f, "Iron keeps the design pressure of 8 atm");
        for (PipeMaterial material : new PipeMaterial[] {PipeMaterial.WROUGHT_IRON, PipeMaterial.LEAD, PipeMaterial.CERAMIC})
        {
            assertTrue(material.safetyValveSetting(8f) < material.rating(1f, ROOM), material + " must lift before it bursts");
        }
        assertTrue(lead < iron && ceramic < lead, "The weaker the body the lower the weight: " + ceramic + ", " + lead + ", " + iron);
        assertTrue(ceramic > 1.5f, "Still above the air pressure of a working line, with the seat 1 atm lower");
    }

    @Test
    void every_material_is_found_by_its_id()
    {
        for (PipeMaterial material : PipeMaterial.values())
        {
            assertSame(material, PipeMaterial.byId(material.id()));
        }
        assertSame(PipeMaterial.WROUGHT_IRON, PipeMaterial.byId("unobtainium"));
    }
}
