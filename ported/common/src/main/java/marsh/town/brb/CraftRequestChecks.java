package marsh.town.brb;

/** Vanilla container state IDs are unsigned 15-bit sequence numbers. */
public final class CraftRequestChecks {
    private CraftRequestChecks() {
    }

    public static boolean newerState(int incoming, int previous) {
        int distance = (incoming - previous) & 32767;
        return distance > 0 && distance < 16384;
    }

    /** Finds a one-to-one ingredient assignment without repeatedly resolving display stacks. */
    public static boolean matchesAssignment(boolean[][] choices) {
        int count = choices.length;
        if (count > 9) return false;
        for (boolean[] row : choices) if (row.length != count) return false;
        return matchesAssignment(choices, 0, 0, new boolean[1 << count]);
    }

    private static boolean matchesAssignment(boolean[][] choices, int ingredient, int used, boolean[] failed) {
        if (ingredient == choices.length) return true;
        if (failed[used]) return false;
        for (int stack = 0; stack < choices.length; stack++) {
            int bit = 1 << stack;
            if ((used & bit) == 0 && choices[ingredient][stack]
                    && matchesAssignment(choices, ingredient + 1, used | bit, failed)) return true;
        }
        failed[used] = true;
        return false;
    }
}
