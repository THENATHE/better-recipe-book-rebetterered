import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.InputStream;
import java.util.Map;

/**
 * Vanilla++ backpack crafting applies required mixins to these BRBR members
 * (components/backpack-crafting compat/Brbr*.java). A change here crashes that suite at startup,
 * so a failure means the suite must be updated together with BRBR.
 */
public final class BackpackCraftingContractChecks {
    private static final String MANAGER = "marsh/town/brb/InstantCraftingManager";
    private static final String PENDING = MANAGER + "$Pending";
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        ClassNode manager = load(MANAGER);
        int craftingMenuChecks = 0;
        for (String descriptor : new String[] {
                "(Lnet/minecraft/world/item/crafting/display/RecipeDisplayId;Z)Z",
                "(Lnet/minecraft/world/item/crafting/display/RecipeDisplayEntry;Ljava/util/function/BooleanSupplier;Z)Z"}) {
            MethodNode method = method(manager, "requestCraft", descriptor);
            int found = count(method, instruction -> instruction instanceof TypeInsnNode type
                    && type.getOpcode() == Opcodes.INSTANCEOF && type.desc.equals("net/minecraft/world/inventory/CraftingMenu"));
            require(found >= 1, "requestCraft" + descriptor + " keeps its CraftingMenu eligibility check");
            craftingMenuChecks += found;
        }
        require(craftingMenuChecks >= 2, "Both requestCraft methods expose the two CraftingMenu checks the suite widens");

        MethodNode storage = method(manager, "isPlayerStorage",
                "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/inventory/Slot;)Z");
        require((storage.access & Opcodes.ACC_STATIC) != 0, "isPlayerStorage stays static");

        // The suite wraps the third read (ordinal 2): the settled-result check before a batch is taken.
        int resultReads = count(method(manager, "tick", "()V"), instruction -> instruction instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETFIELD && field.owner.equals(PENDING) && field.name.equals("receivedResult"));
        require(resultReads == 3, "tick reads Pending.receivedResult exactly three times; review the suite's ordinal 2 wrap");

        ClassNode pending = load(PENDING);
        for (var expected : Map.of(
                "menu", "Lnet/minecraft/world/inventory/AbstractContainerMenu;",
                "resultSlot", "I",
                "maximum", "Z",
                "matchesOutput", "Ljava/util/function/Predicate;",
                "matchesGrid", "Ljava/util/function/BooleanSupplier;",
                "receivedResult", "Z").entrySet()) {
            require(pending.fields.stream().anyMatch(field -> field.name.equals(expected.getKey())
                    && field.desc.equals(expected.getValue())), "Pending." + expected.getKey() + " keeps its name and type");
        }

        ClassNode transfer = load("marsh/town/brb/workstation/WorkstationTransfer");
        MethodNode inventory = method(transfer, "isInventory", "(Lnet/minecraft/world/inventory/Slot;)Z");
        require((inventory.access & Opcodes.ACC_STATIC) != 0, "WorkstationTransfer.isInventory stays static");

        System.out.println("PASS: Vanilla++ backpack crafting mixin targets in BRBR (" + checks + " checks)");
    }

    private static ClassNode load(String name) throws Exception {
        try (InputStream input = ClassLoader.getSystemResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing compiled class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + owner.name + "." + name + descriptor));
    }

    private static int count(MethodNode method, java.util.function.Predicate<AbstractInsnNode> matches) {
        int found = 0;
        for (AbstractInsnNode instruction : method.instructions) if (matches.test(instruction)) found++;
        return found;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
