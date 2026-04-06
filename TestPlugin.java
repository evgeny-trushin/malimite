import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionManager;
import ghidra.program.model.listing.Program;

public class TestPlugin extends GhidraScript {

    @Override
    public void run() throws Exception {
        if (currentProgram == null) {
            println("No program is open. Import a binary and run TestPlugin again.");
            return;
        }

        Program program = currentProgram;
        FunctionManager functionManager = program.getFunctionManager();
        int functionCount = 0;

        println("Listing functions for " + program.getName());

        for (Function function : functionManager.getFunctions(true)) {
            monitor.checkCancelled();

            println(String.format(
                "%s @ %s",
                function.getName(true),
                function.getEntryPoint()
            ));
            functionCount++;
        }

        println("Total functions: " + functionCount);
    }
}
