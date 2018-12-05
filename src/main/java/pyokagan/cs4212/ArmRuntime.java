package pyokagan.cs4212;

import java.util.*;

public class ArmRuntime {

    private static Arm.Prog PRINTLN_INT = new ArmBuilder()
        .global("printf")
        .label("println_int", true)
        .add(new Arm.PushInstr(Arrays.asList(Arm.Reg.R4, Arm.Reg.LR)))
        .add(new Arm.MovInstr(Arm.Reg.R1, Arm.Reg.R0))
        .add(new Arm.LdrLabelInstr(Arm.Reg.R0, ".PD0"))
        .add(new Arm.BLInstr(Arm.Cond.AL, "printf"))
        .add(new Arm.PopInstr(Arrays.asList(Arm.Reg.R4, Arm.Reg.PC)))
        .data()
        .label(".PD0")
        .add(new Arm.AscizInstr("%d\n"))
        .build();

    private static Arm.Prog PRINTLN_BOOL = new ArmBuilder()
        .global("puts")
        .label("println_bool", true)
        .add(new Arm.PushInstr(Arrays.asList(Arm.Reg.R4, Arm.Reg.LR)))
        .add(new Arm.LdrLabelInstr(Arm.Reg.R2, ".PD1"))
        .add(new Arm.LdrLabelInstr(Arm.Reg.R3, ".PD2"))
        .add(new Arm.CmpInstr(Arm.Reg.R0, new Arm.Operand2Const(0)))
        .add(new Arm.MovInstr(Arm.Cond.NE, Arm.Reg.R0, new Arm.Operand2Reg(Arm.Reg.R2)))
        .add(new Arm.MovInstr(Arm.Cond.EQ, Arm.Reg.R0, new Arm.Operand2Reg(Arm.Reg.R3)))
        .add(new Arm.BLInstr(Arm.Cond.AL, "puts"))
        .add(new Arm.PopInstr(Arrays.asList(Arm.Reg.R4, Arm.Reg.PC)))
        .data()
        .label(".PD1")
        .add(new Arm.AscizInstr("true"))
        .label(".PD2")
        .add(new Arm.AscizInstr("false"))
        .build();

    private static Arm.Prog READLN_STRING = new ArmBuilder()
        .global("getchar")
        .label("readln_string", true)
        .add(new Arm.PushInstr(Arrays.asList(Arm.Reg.R4, Arm.Reg.R5, Arm.Reg.R6, Arm.Reg.R7, Arm.Reg.R8, Arm.Reg.LR)))
        .add(new Arm.MovInstr(Arm.Reg.R0, new Arm.Operand2Const(64)))
        .add(new Arm.BLInstr(Arm.Cond.AL, "malloc"))
        .add(new Arm.MovInstr(Arm.Reg.R6, Arm.Reg.R0))
        .add(new Arm.BLInstr(Arm.Cond.AL, "getchar"))
        .add(new Arm.MovInstr(Arm.Reg.R4, new Arm.Operand2Const(0)))
        .add(new Arm.MovInstr(Arm.Reg.R5, Arm.Reg.R0))
        .add(new Arm.MovInstr(Arm.Reg.R7, new Arm.Operand2Const(64)))
        .label(".P12")
        .add(new Arm.CmnInstr(Arm.Reg.R5, new Arm.Operand2Const(1)))
        .add(new Arm.CmpInstr(Arm.Cond.NE, Arm.Reg.R5, new Arm.Operand2Const(10)))
        .add(new Arm.StrbInstr(Arm.Reg.R5, Arm.Reg.R6, Arm.Reg.R4))
        .add(new Arm.AddInstr(Arm.Reg.R3, Arm.Reg.R6, new Arm.Operand2Reg(Arm.Reg.R4)))
        .add(new Arm.AddInstr(Arm.Reg.R4, Arm.Reg.R4, new Arm.Operand2Const(1)))
        .add(new Arm.BInstr(Arm.Cond.EQ, ".P17"))
        .add(new Arm.BLInstr(Arm.Cond.AL, "getchar"))
        .add(new Arm.CmpInstr(Arm.Reg.R7, new Arm.Operand2Reg(Arm.Reg.R4)))
        .add(new Arm.MovInstr(Arm.Reg.R5, new Arm.Operand2Reg(Arm.Reg.R0)))
        .add(new Arm.BInstr(Arm.Cond.HI, ".P12"))
        .add(new Arm.MovInstr(Arm.Reg.R0, new Arm.Operand2Const(2)))
        .add(new Arm.MulInstr(Arm.Reg.R7, Arm.Reg.R7, Arm.Reg.R0))
        .add(new Arm.MovInstr(Arm.Reg.R0, Arm.Reg.R7))
        .add(new Arm.BLInstr(Arm.Cond.AL, "malloc"))
        .add(new Arm.MovInstr(Arm.Reg.R1, Arm.Reg.R6))
        .add(new Arm.MovInstr(Arm.Reg.R2, Arm.Reg.R4))
        .add(new Arm.BLInstr(Arm.Cond.AL, "memcpy"))
        .add(new Arm.MovInstr(Arm.Reg.R6, Arm.Reg.R0))
        .add(new Arm.BInstr(Arm.Cond.AL, ".P12"))
        .label(".P17")
        .add(new Arm.MovInstr(Arm.Reg.R2, new Arm.Operand2Const(0)))
        .add(new Arm.StrbInstr(Arm.Reg.R2, Arm.Reg.R3, null))
        .add(new Arm.MovInstr(Arm.Reg.R0, Arm.Reg.R6))
        .add(new Arm.PopInstr(Arrays.asList(Arm.Reg.R4, Arm.Reg.R5, Arm.Reg.R6, Arm.Reg.R7, Arm.Reg.R8, Arm.Reg.PC)))
        .build();

    private static Arm.Prog READLN_INT = new ArmBuilder()
        .global("readln_string")
        .label("readln_int", true)
        .add(new Arm.PushInstr(Arrays.asList(Arm.Reg.R4, Arm.Reg.LR)))
        .add(new Arm.BLInstr(Arm.Cond.AL, "readln_string"))
        .add(new Arm.MovInstr(Arm.Reg.R2, new Arm.Operand2Const(10)))
        .add(new Arm.MovInstr(Arm.Reg.R1, new Arm.Operand2Const(0)))
        .add(new Arm.BLInstr(Arm.Cond.AL, "strtol"))
        .add(new Arm.PopInstr(Arrays.asList(Arm.Reg.R4, Arm.Reg.PC)))
        .build();

    private static Arm.Prog READLN_BOOL = new ArmBuilder()
        .global("readln_string")
        .label("readln_bool", true)
        .add(new Arm.PushInstr(Arrays.asList(Arm.Reg.R4, Arm.Reg.LR)))
        .add(new Arm.BLInstr(Arm.Cond.AL, "readln_string"))
        .add(new Arm.LdrLabelInstr(Arm.Reg.R1, ".PD3"))
        .add(new Arm.BLInstr(Arm.Cond.AL, "strcmp"))
        .add(new Arm.PopInstr(Arrays.asList(Arm.Reg.R4, Arm.Reg.LR)))
        .add(new Arm.RsbInstr(Arm.Reg.R0, Arm.Reg.R0, new Arm.Operand2Const(1), true))
        .add(new Arm.MovInstr(Arm.Cond.LO, Arm.Reg.R0, new Arm.Operand2Const(0)))
        .add(new Arm.BXInstr(Arm.Cond.AL, Arm.Reg.LR))
        .data()
        .label(".PD3")
        .add(new Arm.AscizInstr("true"))
        .build();

    public static void run(Arm.Prog prog) {
        if (prog.globals.contains("println_int")) {
            addProg(prog, PRINTLN_INT);
            prog.globals.remove("println_int");
        }

        if (prog.globals.contains("println_bool")) {
            addProg(prog, PRINTLN_BOOL);
            prog.globals.remove("println_bool");
        }

        if (prog.globals.contains("readln_int")) {
            addProg(prog, READLN_INT);
            prog.globals.remove("readln_int");
        }

        if (prog.globals.contains("readln_bool")) {
            addProg(prog, READLN_BOOL);
            prog.globals.remove("readln_bool");
        }

        if (prog.globals.contains("readln_string")) {
            addProg(prog, READLN_STRING);
            prog.globals.remove("readln_string");
        }
    }

    private static void addProg(Arm.Prog dst, Arm.Prog src) {
        dst.textBlocks.addAll(src.textBlocks);
        dst.dataBlocks.addAll(src.dataBlocks);
        dst.globals.addAll(src.globals);
    }

    private static class ArmBuilder {
        private ArrayList<Arm.Block> textBlocks = new ArrayList<>();
        private ArrayList<Arm.Block> dataBlocks = new ArrayList<>();
        private Arm.Block currBlock;
        private ArrayList<Arm.Block> curr = textBlocks;
        private ArrayList<String> globals = new ArrayList<>();

        private ArmBuilder global(String name) {
            globals.add(name);
            return this;
        }

        private ArmBuilder label(String name) {
            return label(name, false);
        }

        private ArmBuilder label(String name, boolean special) {
            currBlock = new Arm.Block(name, Collections.emptyList());
            curr.add(currBlock);
            currBlock.special = special;
            return this;
        }

        private ArmBuilder add(Arm.Instr instr) {
            currBlock.instrs.add(instr);
            return this;
        }

        private ArmBuilder data() {
            curr = dataBlocks;
            currBlock = null;
            return this;
        }

        private Arm.Prog build() {
            return new Arm.Prog(textBlocks, dataBlocks, globals);
        }
    }

}
