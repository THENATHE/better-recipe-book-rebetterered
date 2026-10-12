import marsh.town.brb.CraftRequestChecks;

public class CraftRequestChecksTest {
    public static void main(String[] args) {
        for (int previous = 0; previous < 32768; previous++) {
            check(CraftRequestChecks.newerState((previous + 1) & 32767, previous), "forward sequence");
            check(!CraftRequestChecks.newerState(previous, previous), "duplicate sequence");
            check(!CraftRequestChecks.newerState((previous - 1) & 32767, previous), "stale sequence");
            check(!CraftRequestChecks.newerState((previous + 16384) & 32767, previous), "ambiguous sequence");
        }
        check(CraftRequestChecks.matchesAssignment(new boolean[][] {{true, true}, {true, false}}), "overlapping ingredient alternatives need non-greedy assignment");
        check(!CraftRequestChecks.matchesAssignment(new boolean[][] {{true, false}, {true, false}}), "one item cannot satisfy two ingredient slots");
        check(CraftRequestChecks.matchesAssignment(new boolean[0][0]), "empty ingredients");
        check(!CraftRequestChecks.matchesAssignment(new boolean[][] {{true, true}}), "non-square assignment rejected");
        boolean[][] worstCase = new boolean[9][9];
        for (int row = 0; row < 9; row++) for (int col = 0; col < 8; col++) worstCase[row][col] = true;
        check(!CraftRequestChecks.matchesAssignment(worstCase), "nine ingredients cannot share only eight slots");
        worstCase[8][8] = true;
        check(CraftRequestChecks.matchesAssignment(worstCase), "full 3x3 ambiguous matching");
        System.out.println("PASS: 131072 sequence assertions and 6 ingredient assignment regression cases");
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
