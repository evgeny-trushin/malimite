from ghidra.program.model.listing import Function
from ghidra.program.model.symbol import SymbolType
from java.lang import System
import subprocess

def demangle_swift_names(mangled_names):
    os_name = System.getProperty("os.name").lower()
    if "mac" in os_name:
        cmd = ['xcrun', 'swift-demangle', '--simplified', '--compact']
    else:
        cmd = ['swift-demangle', '--simplified', '--compact']

    try:
        # Use subprocess to demangle all names at once
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stdin=subprocess.PIPE, universal_newlines=True)
        stdout, _ = process.communicate(input='\n'.join(mangled_names))
        return stdout.splitlines()
    except Exception as e:
        print("An error occurred during demangling: {}".format(e))
        return []

def clean_demangled_name(name):
    name = name.split("(")[0]
    name = name.replace(" ", "_").replace("<", "_").replace(">", "_").replace("&", "_").replace("[", "_").replace("]", "_").replace(",", "_")
    return name

def beautify_swift_program():
    try:
        # Collect all the mangled names first to batch the demangling process
        mangled_function_names = [func.getName() for func in currentProgram.getFunctionManager().getFunctions(True)]
        mangled_symbol_names = [symbol.getName() for symbol in currentProgram.getSymbolTable().getAllSymbols(True) if symbol.getSymbolType() == SymbolType.LABEL]

        # Demangle all the names at once
        demangled_function_names = demangle_swift_names(mangled_function_names)
        demangled_symbol_names = demangle_swift_names(mangled_symbol_names)

        print("Renaming functions")
        for func, demangled in zip(currentProgram.getFunctionManager().getFunctions(True), demangled_function_names):
            original_name = func.getName()
            cleaned_name = clean_demangled_name(demangled)
            if cleaned_name != original_name:
                comment = "Original: {}\nDemangled: {}".format(original_name, demangled)
                func.setComment(comment)
                func.setName(cleaned_name, ghidra.program.model.symbol.SourceType.USER_DEFINED)
                print("Function renamed from '{}' to '{}'".format(original_name, cleaned_name))

        print("\nRenaming labels. May take some time...")
        for symbol, demangled in zip(currentProgram.getSymbolTable().getAllSymbols(True), demangled_symbol_names):
            if symbol.getSymbolType() == SymbolType.LABEL:
                original_name = symbol.getName()
                cleaned_name = clean_demangled_name(demangled)

                if cleaned_name != original_name:
                    existingSymbols = set(s.getName() for s in currentProgram.getSymbolTable().getSymbols(symbol.getAddress()))

                    unique_name = cleaned_name
                    suffix = 1
                    # Ensure the new name is unique
                    while unique_name in existingSymbols:
                        unique_name = "{}_{}".format(cleaned_name, suffix)
                        suffix += 1

                    comment = "Original: {}\nDemangled: {}".format(original_name, demangled)
                    currentProgram.getListing().setComment(symbol.getAddress(), ghidra.program.model.listing.CodeUnit.EOL_COMMENT, comment)
                    symbol.setName(unique_name, ghidra.program.model.symbol.SourceType.USER_DEFINED)
                    print("Label renamed from '{}' to '{}'".format(original_name, unique_name))
    except Exception as e:
        print("An error occurred: {}".format(e))

beautify_swift_program()
