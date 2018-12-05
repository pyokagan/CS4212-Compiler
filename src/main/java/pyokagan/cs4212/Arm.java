package pyokagan.cs4212;

import java.util.*;
import java.math.*;

public class Arm {
    public static class Prog {
        public final List<Block> textBlocks;
        public final List<Block> dataBlocks;
        public final HashSet<String> globals; // Exported symbols

        public Prog(List<Block> textBlocks, List<Block> dataBlocks, Collection<String> globals) {
            this.textBlocks = new ArrayList<>(textBlocks);
            this.dataBlocks = new ArrayList<>(dataBlocks);
            this.globals = new HashSet<>(globals);
        }

        public String render() {
            StringBuffer sb = new StringBuffer();
            ArrayList<String> sortedGlobals = new ArrayList<>(globals);
            Collections.sort(sortedGlobals);
            for (String global : sortedGlobals)
                sb.append("    .global ").append(global).append('\n');
            sb.append("\n    .text\n");
            for (Block textBlock : textBlocks)
                sb.append(textBlock.render());
            sb.append("\n    .data\n");
            for (Block dataBlock : dataBlocks)
                sb.append(dataBlock.render());
            return sb.toString();
        }
    }

    public static enum Reg {
        R0("r0"),
        R1("r1"),
        R2("r2"),
        R3("r3"),
        R4("r4"),
        R5("r5"),
        R6("r6"),
        R7("r7"),
        R8("r8"),
        R9("r9"),
        R10("r10"),
        R11("r11"),
        R12("r12"),
        SP("sp"),
        LR("lr"),
        PC("pc");

        private final String name;

        private Reg(String name) {
            this.name = name;
        }

        public String render() {
            return name;
        }

        public static Reg fromInteger(int x) {
            switch (x) {
            case 0: return R0;
            case 1: return R1;
            case 2: return R2;
            case 3: return R3;
            case 4: return R4;
            case 5: return R5;
            case 6: return R6;
            case 7: return R7;
            case 8: return R8;
            case 9: return R9;
            case 10: return R10;
            case 11: return R11;
            case 12: return R12;
            }
            throw new AssertionError("Invalid register number " + x);
        }
    }

    public static enum Cond {
        EQ("eq"), // Equal
        NE("ne"), // Not equal
        HS("hs"), // Higher or same, unsigned
        LO("lo"), // Lower, unsigned
        MI("mi"), // Negative
        PL("pl"), // Positive or zero
        VS("vs"), // Overflow
        VC("vc"), // No overflow
        HI("hi"), // Higher, unsigned
        LS("ls"), // Lower or same, unsigned
        GE("ge"), // Greater than or equal, signed
        LT("lt"), // Less than, signed
        GT("gt"), // Greater than, signed
        LE("le"), // Less than or equal, signed
        AL(""); // Can have any value

        private final String suffix;

        private Cond(String suffix) {
            this.suffix = suffix;
        }

        public String render() {
            return suffix;
        }
    }

    public static boolean isValidOperand2Const(int i) {
        // i can be any value that can be produced by rotating an 8-bit value
        // right by an even number of bits within a 32-bit word

        if (i == 0)
            return true;

        // Four cases:
        // 1. Rotate right by 2 bits: 2 bits on MSB side, 6 bits on LSB side
        // 2. Rotate right by 4 bits: 4 bits on MSB side, 4 bits on LSB side
        // 3. Rotate right by 6 bits: 6 bits on MSB side, 2 bits on LSB side
        // 4. Rotate right by 0 or >= 8 bits: Same as shifting left by 0 <= x <= 24 bits,
        // where x is even.

        // Case (4)
        int toShiftLeft = Integer.numberOfTrailingZeros(i) & ~1;
        if ((i & ~(0xff << toShiftLeft)) == 0)
            return true;

        // Case (1), (2), (3)
        return (i & ~0xc000003f) == 0 || (i & ~0xf000000f) == 0 || (i & ~0xfc000003) == 0;
    }

    public static abstract class Operand2 {
        public abstract String render();
    }
    public static class Operand2Const extends Operand2 {
        public final int i;

        public Operand2Const(int i) {
            if (!isValidOperand2Const(i))
                throw new AssertionError("Not a valid Operand2Const: " + i);
            this.i = i;
        }

        @Override
        public String render() {
            return "#" + i;
        }
    }
    public static class Operand2Reg extends Operand2 {
        public final Reg reg;

        public Operand2Reg(Reg reg) {
            this.reg = reg;
        }

        @Override
        public String render() {
            return reg.render();
        }
    }

    public static class Block {
        String name; // The block (label) name
        public ArrayList<Instr> instrs;
        boolean special;

        public Block(String name, List<Instr> instrs) {
            this.name = name;
            this.instrs = new ArrayList<>(instrs);
        }

        public String render() {
            StringBuffer sb = new StringBuffer();
            if (special)
                sb.append('\n');
            sb.append(name).append(":\n");
            for (Instr instr : instrs)
                sb.append(instr.render());
            return sb.toString();
        }
    }

    public static abstract class Instr {
        public abstract String render();
    }

    public static class AddInstr extends Instr {
        public final Reg dst;
        public final Reg a;
        public final Operand2 b;
        public final boolean s;

        public AddInstr(Reg dst, Reg a, Operand2 b) {
            this(dst, a, b, false);
        }

        public AddInstr(Reg dst, Reg a, Operand2 b, boolean s) {
            this.dst = dst;
            this.a = a;
            this.b = b;
            this.s = s;
        }

        @Override
        public String render() {
            String name = s ? "adds" : "add";
            return "    " + name + " " + dst.render() + ", " +  a.render() + ", " + b.render() + "\n";
        }
    }

    public static class SubInstr extends Instr {
        public final Reg dst;
        public final Reg a;
        public final Operand2 b;
        public final boolean s;

        public SubInstr(Reg dst, Reg a, Operand2 b) {
            this(dst, a, b, false);
        }

        public SubInstr(Reg dst, Reg a, Operand2 b, boolean s) {
            this.dst = dst;
            this.a = a;
            this.b = b;
            this.s = s;
        }

        @Override
        public String render() {
            String name = s ? "subs" : "sub";
            return "    " + name + " " + dst.render() + ", " + a.render() + ", " + b.render() + "\n";
        }
    }

    public static class RsbInstr extends Instr {
        public final Reg dst;
        public final Reg a;
        public final Operand2 b;
        public final boolean s;

        public RsbInstr(Reg dst, Reg a, Operand2 b) {
            this(dst, a, b, false);
        }

        public RsbInstr(Reg dst, Reg a, Operand2 b, boolean s) {
            this.dst = dst;
            this.a = a;
            this.b = b;
            this.s = s;
        }

        @Override
        public String render() {
            return "    rsb" + (s ? "s" : "") + " " + dst.render() + ", " + a.render() + ", " + b.render() + "\n";
        }
    }

    public static class MulInstr extends Instr {
        public final Reg dst;
        public final Reg a;
        public final Reg b;
        public final boolean s;

        public MulInstr(Reg dst, Reg a, Reg b) {
            this(dst, a, b, false);
        }

        public MulInstr(Reg dst, Reg a, Reg b, boolean s) {
            this.dst = dst;
            this.a = a;
            this.b = b;
            this.s = s;
        }

        @Override
        public String render() {
            String name = s ? "muls" : "mul";
            return "    " + name + " " + dst.render() + ", " + a.render() + ", " + b.render() + "\n";
        }
    }

    public static class MovInstr extends Instr {
        public Cond cond;
        public final Reg dst;
        public final Operand2 src;

        public MovInstr(Reg dst, Operand2 src) {
            this(Arm.Cond.AL, dst, src);
        }

        public MovInstr(Reg dst, Reg src) {
            this(Arm.Cond.AL, dst, new Operand2Reg(src));
        }

        public MovInstr(Cond cond, Reg dst, Operand2 src) {
            this.cond = cond;
            this.dst = dst;
            this.src = src;
        }

        @Override
        public String render() {
            return "    mov" + cond.render() + " " + dst.render() + ", " + src.render() + "\n";
        }
    }

    public static class MovConstInstr extends Instr {
        public final Reg dst;
        public final int i;

        public MovConstInstr(Reg dst, int i) {
            if (i < 0 || i > 65535)
                throw new AssertionError("MovConstInstr: must be 0-65535: " + i);
            this.dst = dst;
            this.i = i;
        }

        @Override
        public String render() {
            return "    mov " + dst.render() + ", #" + i + "\n";
        }
    }

    public static class BInstr extends Instr {
        public Cond cond;
        public String label;

        public BInstr(Cond cond, String label) {
            this.cond = cond;
            this.label = label;
        }

        @Override
        public String render() {
            return "    b" + cond.render() + " " + label + "\n";
        }
    }

    public static class BLInstr extends Instr {
        public final Cond cond;
        public final String label;

        public BLInstr(Cond cond, String label) {
            this.cond = cond;
            this.label = label;
        }

        @Override
        public String render() {
            return "    bl" + cond.render() + " " + label + "\n";
        }
    }

    public static class BXInstr extends Instr {
        public final Cond cond;
        public final Reg reg;

        public BXInstr(Cond cond, Reg reg) {
            this.cond = cond;
            this.reg = reg;
        }

        @Override
        public String render() {
            return "    bx" + cond.render() + " " + reg.render() + "\n";
        }
    }

    public static class LdrInstr extends Instr {
        public final Reg reg;
        public final Reg mem;
        public final int offset;

        public LdrInstr(Reg reg, Reg mem, int offset) {
            this.reg = reg;
            this.mem = mem;
            this.offset = offset;
        }

        @Override
        public String render() {
            if (offset == 0)
                return "    ldr " + reg.render() + ", [" + mem.render() + "]\n";
            else
                return "    ldr " + reg.render() + ", [" + mem.render() + ", #" + offset + "]\n";
        }
    }

    public static class LdrConstInstr extends Instr {
        public final Reg reg;
        public final int i;

        public LdrConstInstr(Reg reg, int i) {
            this.reg = reg;
            this.i = i;
        }

        @Override
        public String render() {
            return "    ldr " + reg.render() + ", =" + i + "\n";
        }
    }

    public static class LdrLabelInstr extends Instr {
        public final Reg reg;
        public final String label;

        public LdrLabelInstr(Reg reg, String label) {
            this.reg = reg;
            this.label = label;
        }

        @Override
        public String render() {
            return "    ldr " + reg.render() + ", =" + label + "\n";
        }
    }

    public static class StrInstr extends Instr {
        public final Reg reg;
        public final Reg mem;
        public final int offset;

        public StrInstr(Reg reg, Reg mem, int offset) {
            this.reg = reg;
            this.mem = mem;
            this.offset = offset;
        }

        @Override
        public String render() {
            if (offset == 0)
                return "    str " + reg.render() + ", [" + mem.render() + "]\n";
            else
                return "    str " + reg.render() + ", [" + mem.render() + ", #" + offset + "]\n";
        }
    }

    public static class StrbInstr extends Instr {
        public final Reg reg;
        public final Reg mem;
        public final Reg offset;

        public StrbInstr(Reg reg, Reg mem, Reg offset) {
            this.reg = reg;
            this.mem = mem;
            this.offset = offset;
        }

        @Override
        public String render() {
            if (offset == null)
                return "    strb " + reg.render() + ", [" + mem.render() + "]\n";
            else
                return "    strb " + reg.render() + ", [" + mem.render() + ", " + offset.render() + "]\n";
        }
    }

    public static class CmpInstr extends Instr {
        public Cond cond;
        public final Reg a;
        public final Operand2 b;

        public CmpInstr(Reg a, Operand2 b) {
            this(Arm.Cond.AL, a, b);
        }

        public CmpInstr(Cond cond, Reg a, Operand2 b) {
            this.cond = cond;
            this.a = a;
            this.b = b;
        }

        @Override
        public String render() {
            return "    cmp" + cond.render() + " " + a.render() + ", " + b.render() + "\n";
        }
    }

    public static class CmnInstr extends Instr {
        public final Reg a;
        public final Operand2 b;

        public CmnInstr(Reg a, Operand2 b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public String render() {
            return "    cmn " + a.render() + ", " + b.render() + "\n";
        }
    }

    public static class PushInstr extends Instr {
        public final Set<Reg> regs;

        public PushInstr(Collection<Reg> regs) {
            if (regs.isEmpty())
                throw new AssertionError("PushInstr: regs must be non-empty");
            this.regs = new HashSet<>(regs);
        }

        @Override
        public String render() {
            ArrayList<Reg> reglist = new ArrayList<>(regs);
            Collections.sort(reglist);
            StringBuilder sb = new StringBuilder();
            sb.append("    push {");
            int i = 0;
            for (Reg reg : reglist) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(reg.render());
            }
            sb.append("}\n");
            return sb.toString();
        }
    }

    public static class PopInstr extends Instr {
        public final Set<Reg> regs;

        public PopInstr(Collection<Reg> regs) {
            if (regs.isEmpty())
                throw new AssertionError("PopInstr: regs must be non-empty");
            this.regs = new HashSet<>(regs);
        }

        @Override
        public String render() {
            ArrayList<Reg> reglist = new ArrayList<>(regs);
            Collections.sort(reglist);
            StringBuilder sb = new StringBuilder();
            sb.append("    pop {");
            int i = 0;
            for (Reg reg : reglist) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(reg.render());
            }
            sb.append("}\n");
            return sb.toString();
        }
    }

    public static class AscizInstr extends Instr {
        public final String s;

        public AscizInstr(String s) {
            this.s = s;
        }

        @Override
        public String render() {
            return "    .asciz \"" + Ast.escape(s) + "\"\n";
        }
    }
}
