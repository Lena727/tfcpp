package tfcpp.pneumatics.pipes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which of the pipes that touch one another make one network, once two different liquids are not allowed to meet. Free of Minecraft, so that the rule can be tested on any shape.
 * <p>
 * A pipe with a liquid in it takes the part of that liquid. A pipe with none (a run of such pipes, to be exact: pipes that are empty and joined to each other) goes with a liquid it touches, and if it touches two or more different ones it goes with the one whose id comes first in the alphabet. Both ends of the run get the same answer, wherever the rebuild begins, so the networks do not depend on which pipe happened to be looked at first. Pipes with different liquids are not joined: that edge is a closed joint between two runs.
 */
public final class LiquidRuns
{
    /**
     * @param start    the pipe to find the network of
     * @param joined   the pipes that are open to this one
     * @param liquidOf the id of the liquid a pipe holds, or an empty string
     * @return every pipe that is in the same network as the start, the start first
     */
    public static <T> List<T> network(T start, Function<T, List<T>> joined, Function<T, String> liquidOf)
    {
        final Map<T, List<T>> neighbours = new HashMap<>();
        final Function<T, List<T>> open = node -> neighbours.computeIfAbsent(node, joined);
        final Map<T, String> labels = new HashMap<>();

        if (liquidOf.apply(start).isEmpty())
        {
            final Region<T> region = explore(start, open, liquidOf, labels);
            if (region.label.isEmpty())
            {
                return region.members; // No liquid anywhere by it: the run is the network
            }
        }
        final String label = labelOf(start, open, liquidOf, labels);

        final List<T> found = new ArrayList<>();
        final Set<T> seen = new HashSet<>();
        final Deque<T> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty())
        {
            final T node = queue.poll();
            found.add(node);
            for (T next : open.apply(node))
            {
                if (!seen.contains(next) && labelOf(next, open, liquidOf, labels).equals(label))
                {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    private record Region<T>(List<T> members, String label) {}

    private static <T> String labelOf(T node, Function<T, List<T>> open, Function<T, String> liquidOf, Map<T, String> labels)
    {
        final String known = labels.get(node);
        if (known != null)
        {
            return known;
        }
        final String own = liquidOf.apply(node);
        if (!own.isEmpty())
        {
            labels.put(node, own);
            return own;
        }
        return explore(node, open, liquidOf, labels).label;
    }

    /** The run of empty pipes that this one is in, with the liquid it goes with. Every member is given its label at once. */
    private static <T> Region<T> explore(T start, Function<T, List<T>> open, Function<T, String> liquidOf, Map<T, String> labels)
    {
        final List<T> members = new ArrayList<>();
        final Set<T> seen = new HashSet<>();
        final Deque<T> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        String least = null;
        while (!queue.isEmpty())
        {
            final T node = queue.poll();
            members.add(node);
            for (T next : open.apply(node))
            {
                final String liquid = liquidOf.apply(next);
                if (liquid.isEmpty())
                {
                    if (seen.add(next))
                    {
                        queue.add(next);
                    }
                }
                else if (least == null || liquid.compareTo(least) < 0)
                {
                    least = liquid;
                }
            }
        }
        final String label = least == null ? "" : least;
        for (T member : members)
        {
            labels.put(member, label);
        }
        return new Region<>(members, label);
    }

    private LiquidRuns() {}
}
