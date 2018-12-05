package pyokagan.cs4212;

import java.util.*;

/**
 * Generates ARM.
 */
public class ArmGen {
    private ArrayList<Arm.Block> textBlocks = new ArrayList<>();
    private ArrayList<Arm.Block> dataBlocks = new ArrayList<>();
    private Arm.Block currBlock;
    private HashSet<String> globals = new HashSet<>();

    private HashMap<Ir.Block, String> blockToLabelName;
    private HashMap<Ir.PhiWeb, Integer> stackOffsets;
    private HashMap<String, HashMap<String, Integer>> fieldOffsets = new HashMap<>();
    private HashMap<String, String> stringToLabelName = new HashMap<>();
    private String epilogueLabel;
    private int labelIdx;
    private HashSet<Arm.Reg> calleeRegs;
    private LivePass livePass;
    private boolean isMain;
    private HashSet<Ir.Block> visited;
    private ArrayList<Ir.Block> blocksPo;

    public static Arm.Prog run(Ir.Prog prog) {
        ArmGen ag = new ArmGen();

        // Compute field offsets
        for (Ir.Data data : prog.datas) {
            HashMap<String, Integer> offsets = new HashMap<>();
            int offset = 0;
            for (Ir.DataField field : data.fields) {
                offsets.put(field.name, offset);
                offset += 4;
            }
            ag.fieldOffsets.put(data.cname, offsets);
        }

        ag.globals.add("main");

        for (Ir.Meth meth : prog.meths)
            ag.doMeth(meth);

        ArrayList<String> globals = new ArrayList<>(ag.globals);
        Collections.sort(globals);

        return new Arm.Prog(ag.textBlocks, ag.dataBlocks, globals);
    }

    private void doMeth(Ir.Meth meth) {
        // Generate block for meth prologue
        Arm.Block prologueBlock = new Arm.Block(methNameToLabel(meth.name), new ArrayList<>());
        textBlocks.add(prologueBlock);
        prologueBlock.special = true;
        isMain = meth.name.equals("main");
        currBlock = prologueBlock;

        // Perform livePass
        livePass = new LivePass(meth);

        // Compute label names for blocks
        blockToLabelName = new HashMap<>();
        for (Ir.Block block : meth.blocks) {
            blockToLabelName.put(block, genLabel());
        }

        // Determine the set of callee-saved registers we use
        calleeRegs = new HashSet<>();
        for (Ir.Var v : meth.locals) {
            if (v.reg >= 0) // PHIMEM will have no allocated register
                calleeRegs.add(toReg(v));
        }
        calleeRegs.remove(Arm.Reg.R0);
        calleeRegs.remove(Arm.Reg.R1);
        calleeRegs.remove(Arm.Reg.R2);
        calleeRegs.remove(Arm.Reg.R3);

        // Determine amount of stack space to allocate
        int maxArg = -1;
        HashSet<Ir.PhiWeb> phiWebsNeedStack = new HashSet<>();
        for (Ir.Block block : meth.blocks) {
            for (Ir.Stmt stmt : block.stmts) {
                if (stmt instanceof Ir.SpillStmt) {
                    Ir.SpillStmt spillStmt = (Ir.SpillStmt) stmt;
                    if (spillStmt.v.phiWeb == null)
                        throw new AssertionError("BUG: Var " + spillStmt.v.render() + " was spilled but has no phiweb");
                    phiWebsNeedStack.add(spillStmt.v.phiWeb);
                } else if (stmt instanceof Ir.ReloadStmt) {
                    Ir.ReloadStmt reloadStmt = (Ir.ReloadStmt) stmt;
                    if (reloadStmt.getDst().phiWeb == null)
                        throw new AssertionError("BUG: Var " + reloadStmt.getDst().render() + " was reloaded but has no phiweb");
                    phiWebsNeedStack.add(reloadStmt.getDst().phiWeb);
                } else if (stmt instanceof Ir.StackArgStmt) {
                    Ir.StackArgStmt stackArgStmt = (Ir.StackArgStmt) stmt;
                    maxArg = Math.max(stackArgStmt.idx, maxArg);
                } else if (stmt instanceof Ir.CallStmt) {
                    // Non-leaf function -- Must save link register
                    calleeRegs.add(Arm.Reg.LR);
                }
            }
        }

        int numStack, stackSub;

        numStack = (maxArg + 1) + phiWebsNeedStack.size() + calleeRegs.size();
        if (numStack % 2 == 1)
            numStack++; // Must be a multiple of 2 to have 8-byte stack alignment
        stackSub = (numStack - calleeRegs.size()) * 4;
        if (!Arm.isValidOperand2Const(stackSub) && stackSub > 65535) {
            calleeRegs.add(Arm.Reg.R4);
            numStack = (maxArg + 1) + phiWebsNeedStack.size() + calleeRegs.size();
            if (numStack % 2 == 1)
                numStack++;
            stackSub = (numStack - calleeRegs.size()) * 4;
        }

        // Give out stack space to phiwebs
        int stackOffset = (maxArg + 1) * 4; // Top of the stack is used for arguments for calling other functions
        stackOffsets = new HashMap<>();
        for (Ir.PhiWeb phiWeb : phiWebsNeedStack) {
            stackOffsets.put(phiWeb, stackOffset);
            stackOffset += 4;
        }
        // PhiWebs belonging to arguments of this function have special locations
        for (int i = 4; i < meth.args.size(); i++) {
            Ir.Var arg = meth.args.get(i);
            if (arg.phiWeb == null)
                throw new AssertionError("BUG: Var " + arg.render() + " is an argument but has no phiweb");
            stackOffsets.put(arg.phiWeb, (numStack + i - 4) * 4);
        }

        // == Generate preamble ==
        // Push callee-saved regs
        if (!calleeRegs.isEmpty())
            currBlock.instrs.add(new Arm.PushInstr(calleeRegs));
        // Allocate stack
        if (stackSub == 0) {
            // Do nothing
        } else if (Arm.isValidOperand2Const(stackSub)) {
            // SUB directly
            currBlock.instrs.add(new Arm.SubInstr(Arm.Reg.SP, Arm.Reg.SP, new Arm.Operand2Const(stackSub)));
        } else {
            // MOV/LDR then SUB
            emitAssign(Arm.Reg.R4, stackSub);
            currBlock.instrs.add(new Arm.LdrConstInstr(Arm.Reg.R4, stackSub));
            currBlock.instrs.add(new Arm.SubInstr(Arm.Reg.SP, Arm.Reg.SP, new Arm.Operand2Reg(Arm.Reg.R4)));
        }

        // Generate a reverse post ordering of blocks that prioritizes the "else" branch
        visited = new HashSet<>();
        blocksPo = new ArrayList<>();
        blockDfs(meth.blocks.get(0));

        // Generate epilogue label
        epilogueLabel = genLabel();

        // End the prologue with a jump to the first block (or the epilogue)
        if (blocksPo.isEmpty()) {
            currBlock.instrs.add(new Arm.BInstr(Arm.Cond.AL, epilogueLabel));
        } else {
            currBlock.instrs.add(new Arm.BInstr(Arm.Cond.AL, blockToLabelName.get(blocksPo.get(blocksPo.size() - 1))));
        }

        // Generate blocks
        for (int i = blocksPo.size() - 1; i >= 0; i--)
            doBlock(blocksPo.get(i));

        // == Generate epilogue ==
        Arm.Block epilogueBlock = new Arm.Block(epilogueLabel, new ArrayList<>());
        textBlocks.add(epilogueBlock);
        currBlock = epilogueBlock;
        // Deallocate stack back
        if (stackSub == 0) {
            // Do nothing
        } else if (Arm.isValidOperand2Const(stackSub)) {
            // ADD directly
            currBlock.instrs.add(new Arm.AddInstr(Arm.Reg.SP, Arm.Reg.SP, new Arm.Operand2Const(stackSub)));
        } else {
            // MOV/LDR then ADD
            emitAssign(Arm.Reg.R4, stackSub);
            currBlock.instrs.add(new Arm.AddInstr(Arm.Reg.SP, Arm.Reg.SP, new Arm.Operand2Reg(Arm.Reg.R4)));
        }
        // Pop callee-saved registers and return
        if (calleeRegs.contains(Arm.Reg.LR)) {
            if (isMain)
                emitAssign(Arm.Reg.R0, 0);
            // Convert into PC
            calleeRegs.remove(Arm.Reg.LR);
            calleeRegs.add(Arm.Reg.PC);
            // A single POP
            currBlock.instrs.add(new Arm.PopInstr(calleeRegs));
        } else {
            if (!calleeRegs.isEmpty())
                currBlock.instrs.add(new Arm.PopInstr(calleeRegs));
            if (isMain)
                emitAssign(Arm.Reg.R0, 0);
            currBlock.instrs.add(new Arm.BXInstr(Arm.Cond.AL, Arm.Reg.LR));
        }
    }

    private void blockDfs(Ir.Block block) {
        visited.add(block);
        // Do outgoingCond first so that the reverse postorder will take the outgoingDirect branch first
        if (block.outgoingCond != null && !visited.contains(block.outgoingCond))
            blockDfs(block.outgoingCond);
        if (block.outgoingDirect != null && !visited.contains(block.outgoingDirect))
            blockDfs(block.outgoingDirect);
        blocksPo.add(block);
    }

    private void doBlock(Ir.Block block) {
        currBlock = new Arm.Block(blockToLabelName.get(block), new ArrayList<>());
        for (Ir.Stmt stmt : block.stmts)
            doStmt(block, stmt);
        textBlocks.add(currBlock);
    }

    private void doStmt(Ir.Block block, Ir.Stmt stmt) {
        if (stmt instanceof Ir.CmpStmt) {
            Ir.CmpStmt cmpStmt = (Ir.CmpStmt) stmt;
            String targetLabel = blockToLabelName.get(Objects.requireNonNull(block.outgoingCond));
            String fallthroughLabel = blockToLabelName.get(Objects.requireNonNull(block.outgoingDirect));

            if (isConstant(cmpStmt.a) && isConstant(cmpStmt.b)) {
                int a = toIntConstant(cmpStmt.a);
                int b = toIntConstant(cmpStmt.b);
                boolean result;

                switch (cmpStmt.op) {
                case LT:
                    result = a < b;
                    break;
                case GT:
                    result = a > b;
                    break;
                case LE:
                    result = a <= b;
                    break;
                case GE:
                    result = a >= b;
                    break;
                case EQ:
                    result = a == b;
                    break;
                case NE:
                    result = a != b;
                    break;
                default:
                    throw new AssertionError("BUG");
                }

                if (result) {
                    // Direct jump
                    currBlock.instrs.add(new Arm.BInstr(Arm.Cond.AL, targetLabel));
                } else {
                    // Fallthrough
                    currBlock.instrs.add(new Arm.BInstr(Arm.Cond.AL, fallthroughLabel));
                }
            } else {
                Arm.Reg a = toReg(cmpStmt.a);
                Arm.Operand2 b = toOperand2(cmpStmt.b);

                currBlock.instrs.add(new Arm.CmpInstr(a, b));
                currBlock.instrs.add(new Arm.BInstr(opToCond(cmpStmt.op), targetLabel));
                currBlock.instrs.add(new Arm.BInstr(Arm.Cond.AL, fallthroughLabel));
            }
        } else if (stmt instanceof Ir.GotoStmt) {
            String targetLabel = blockToLabelName.get(Objects.requireNonNull(block.outgoingDirect));

            // Build register transfer graph for output
            ArrayList<ParallelCopy> copies = new ArrayList<>();
            int incomingIdx = block.outgoingDirect.incoming.indexOf(block);
            HashSet<Ir.Var> phiSrcs = new HashSet<>();
            for (Ir.Stmt outgoingStmt : block.outgoingDirect.stmts) {
                if (!(outgoingStmt instanceof Ir.PhiStmt))
                    break;
                Ir.PhiStmt phiStmt = (Ir.PhiStmt) outgoingStmt;
                if (phiStmt.memory || phiStmt.args.get(incomingIdx) == null)
                    continue;
                phiSrcs.add(phiStmt.args.get(incomingIdx));
                copies.add(new ParallelCopy(toReg(phiStmt.args.get(incomingIdx)), toReg(phiStmt.getDst())));
            }
            // Vars that are live out (though not because of a phi node) need their register contents preserved as well
            for (Ir.Var v : livePass.getLiveOut(block)) {
                if (!phiSrcs.contains(v))
                    copies.add(new ParallelCopy(toReg(v), toReg(v)));
            }
            doParallelCopies(copies);

            currBlock.instrs.add(new Arm.BInstr(Arm.Cond.AL, targetLabel));
        } else if (stmt instanceof Ir.BinaryStmt) {
            Ir.BinaryStmt binaryStmt = (Ir.BinaryStmt) stmt;
            Arm.Reg dst = toReg(binaryStmt.getDst());

            if (isConstant(binaryStmt.a) && isConstant(binaryStmt.b)) {
                int a = toIntConstant(binaryStmt.a);
                int b = toIntConstant(binaryStmt.b);
                int result;

                switch (binaryStmt.op) {
                case PLUS:
                    result = a + b;
                    break;
                case MINUS:
                    result = a - b;
                    break;
                case MUL:
                    result = a * b;
                    break;
                case DIV:
                    result = a / b;
                    break;
                default:
                    throw new AssertionError("BUG");
                }

                emitAssign(dst, result);
            } else if (binaryStmt.op == Ir.BinaryOp.PLUS) {
                Arm.Reg a = toReg(binaryStmt.a);
                Arm.Operand2 b = toOperand2(binaryStmt.b);

                currBlock.instrs.add(new Arm.AddInstr(dst, a, b));
            } else if (binaryStmt.op == Ir.BinaryOp.MINUS) {
                Arm.Reg a = toReg(binaryStmt.a);
                Arm.Operand2 b = toOperand2(binaryStmt.b);

                currBlock.instrs.add(new Arm.SubInstr(dst, a, b));
            } else if (binaryStmt.op == Ir.BinaryOp.RSB) {
                Arm.Reg a = toReg(binaryStmt.a);
                Arm.Operand2 b = toOperand2(binaryStmt.b);

                currBlock.instrs.add(new Arm.RsbInstr(dst, a, b));
            } else if (binaryStmt.op == Ir.BinaryOp.MUL) {
                Arm.Reg a = toReg(binaryStmt.a);
                Arm.Reg b = toReg(binaryStmt.b);

                currBlock.instrs.add(new Arm.MulInstr(dst, a, b));
            } else {
                throw new AssertionError("invalid binaryStmt op " + binaryStmt.op);
            }
        } else if (stmt instanceof Ir.UnaryStmt) {
            Ir.UnaryStmt unaryStmt = (Ir.UnaryStmt) stmt;
            Arm.Reg dst = toReg(unaryStmt.getDst());

            if (isConstant(unaryStmt.a)) {
                int a = toIntConstant(unaryStmt.a);
                int result;

                switch (unaryStmt.op) {
                case NEG:
                    result = -a;
                    break;
                default:
                    throw new AssertionError("BUG");
                }

                emitAssign(dst, result);
            } else if (unaryStmt.op == Ir.UnaryOp.NEG) {
                Arm.Reg a = toReg(unaryStmt.a);

                currBlock.instrs.add(new Arm.RsbInstr(dst, a, new Arm.Operand2Const(0)));
            } else {
                throw new AssertionError("invalid unaryStmt op " + unaryStmt.op);
            }
        } else if (stmt instanceof Ir.FieldAccessStmt) {
            Ir.FieldAccessStmt fieldAccessStmt = (Ir.FieldAccessStmt) stmt;
            Arm.Reg dst = toReg(fieldAccessStmt.getDst());
            Arm.Reg mem = toReg(fieldAccessStmt.target);
            Ast.ClazzTyp typ = (Ast.ClazzTyp) fieldAccessStmt.target.getTyp();
            int offset = fieldOffsets.get(typ.cname).get(fieldAccessStmt.field);

            currBlock.instrs.add(new Arm.LdrInstr(dst, mem, offset));
        } else if (stmt instanceof Ir.FieldAssignStmt) {
            Ir.FieldAssignStmt fieldAssignStmt = (Ir.FieldAssignStmt) stmt;
            Arm.Reg mem = toReg(fieldAssignStmt.dst);
            Ast.ClazzTyp typ = (Ast.ClazzTyp) fieldAssignStmt.dst.typ;
            int offset = fieldOffsets.get(typ.cname).get(fieldAssignStmt.field);
            Arm.Reg src = toReg(fieldAssignStmt.src);

            currBlock.instrs.add(new Arm.StrInstr(src, mem, offset));
        } else if (stmt instanceof Ir.AssignStmt) {
            Ir.AssignStmt assignStmt = (Ir.AssignStmt) stmt;
            Arm.Reg dst = toReg(assignStmt.getDst());

            emitAssign(dst, assignStmt.src);
        } else if (stmt instanceof Ir.ReturnStmt) {
            Ir.ReturnStmt returnStmt = (Ir.ReturnStmt) stmt;

            if (returnStmt.rv != null) {
                emitAssign(Arm.Reg.R0, returnStmt.rv);
            }

            currBlock.instrs.add(new Arm.BInstr(Arm.Cond.AL, epilogueLabel));
        } else if (stmt instanceof Ir.MethCallStmt) {
            Ir.MethCallStmt methCallStmt = (Ir.MethCallStmt) stmt;

            currBlock.instrs.add(new Arm.BLInstr(Arm.Cond.AL, methNameToLabel(methCallStmt.meth.name)));
        } else if (stmt instanceof Ir.ExternCallStmt) {
            Ir.ExternCallStmt externCallStmt = (Ir.ExternCallStmt) stmt;

            currBlock.instrs.add(new Arm.BLInstr(Arm.Cond.AL, externCallStmt.target));
            globals.add(externCallStmt.target);
        } else if (stmt instanceof Ir.CallPrepStmt) {
            Ir.CallPrepStmt callPrepStmt = (Ir.CallPrepStmt) stmt;

            ArrayList<ParallelCopy> copies = new ArrayList<>();
            for (int i = 0; i < callPrepStmt.defs.size(); i++) {
                Arm.Reg dst = toReg(callPrepStmt.defs.get(i));
                Arm.Reg src = toReg(callPrepStmt.srcs.get(i));
                copies.add(new ParallelCopy(src, dst));
            }

            doParallelCopies(copies);
        } else if (stmt instanceof Ir.PhiStmt) {
            // Do nothing -- Phi stmts are handled at the end of blocks
        } else if (stmt instanceof Ir.SpillStmt) {
            Ir.SpillStmt spillStmt = (Ir.SpillStmt) stmt;
            Arm.Reg src = toReg(spillStmt.v);
            int offset = stackOffsets.get(spillStmt.v.phiWeb);

            currBlock.instrs.add(new Arm.StrInstr(src, Arm.Reg.SP, offset));
        } else if (stmt instanceof Ir.ReloadStmt) {
            Ir.ReloadStmt reloadStmt = (Ir.ReloadStmt) stmt;
            Arm.Reg dst = toReg(reloadStmt.getDst());
            int offset = stackOffsets.get(reloadStmt.getDst().phiWeb);

            currBlock.instrs.add(new Arm.LdrInstr(dst, Arm.Reg.SP, offset));
        } else if (stmt instanceof Ir.StackArgStmt) {
            Ir.StackArgStmt stackArgStmt = (Ir.StackArgStmt) stmt;
            Arm.Reg src = toReg(stackArgStmt.v);
            int offset = stackArgStmt.idx * 4;

            currBlock.instrs.add(new Arm.StrInstr(src, Arm.Reg.SP, offset));
        } else {
            throw new AssertionError("unsupported stmt: " + stmt.render(0));
        }
    }

    private static Arm.Cond opToCond(Ir.CondOp op) {
        switch (op) {
        case LT:
            return Arm.Cond.LT;
        case GT:
            return Arm.Cond.GT;
        case LE:
            return Arm.Cond.LE;
        case GE:
            return Arm.Cond.GE;
        case EQ:
            return Arm.Cond.EQ;
        case NE:
            return Arm.Cond.NE;
        default:
            throw new AssertionError("BUG");
        }
    }

    private void emitAssign(Arm.Reg dst, Arm.Reg src) {
        if (dst == src)
            return;
        currBlock.instrs.add(new Arm.MovInstr(Arm.Cond.AL, dst, new Arm.Operand2Reg(src)));
    }

    private void emitAssign(Arm.Reg dst, Ir.Rval rv) {
        if (rv instanceof Ir.StringLitRval) {
            String s = ((Ir.StringLitRval) rv).str;
            if (!stringToLabelName.containsKey(s)) {
                // Create string and add to data section
                String labelName = genLabel();
                stringToLabelName.put(s, labelName);
                dataBlocks.add(new Arm.Block(labelName, Arrays.asList(new Arm.AscizInstr(s))));
            }
            String labelName = stringToLabelName.get(s);
            currBlock.instrs.add(new Arm.LdrLabelInstr(dst, labelName));
        } else if (rv instanceof Ir.VarRval) {
            emitAssign(dst, toReg(rv));
        } else {
            int i = toIntConstant(rv);
            emitAssign(dst, i);
        }
    }

    private void emitAssign(Arm.Reg dst, int i) {
        if (Arm.isValidOperand2Const(i)) {
            currBlock.instrs.add(new Arm.MovInstr(Arm.Cond.AL, dst, new Arm.Operand2Const(i)));
        } else if (i >= 0 && i <= 65535) {
            currBlock.instrs.add(new Arm.MovConstInstr(dst, i));
        } else {
            currBlock.instrs.add(new Arm.LdrConstInstr(dst, i));
        }
    }

    public static boolean isValidOperand2Const(Ir.Rval rv) {
        if (rv instanceof Ir.NullLitRval) {
            return true;
        } else if (rv instanceof Ir.BoolLitRval) {
            return true;
        } else if (rv instanceof Ir.IntLitRval) {
            return Arm.isValidOperand2Const(((Ir.IntLitRval) rv).i);
        } else {
            return false;
        }
    }

    /**
     * Returns true if rv can be resolved at ArmGen time.
     */
    public static boolean isConstant(Ir.Rval rv) {
        if (rv instanceof Ir.NullLitRval) {
            return true;
        } else if (rv instanceof Ir.BoolLitRval) {
            return true;
        } else if (rv instanceof Ir.IntLitRval) {
            return true;
        } else {
            return false;
        }
    }

    private static Arm.Reg toReg(Ir.Var v) {
        if (v.reg < 0)
            throw new AssertionError("BUG: Var " + v.render() + " has no allocated register!");
        return Arm.Reg.fromInteger(v.reg);
    }

    private static Arm.Reg toReg(Ir.Rval rv) {
        return toReg(((Ir.VarRval) rv).v);
    }

    private static Arm.Operand2 toOperand2(Ir.Rval rv) {
        if (isConstant(rv)) {
            return new Arm.Operand2Const(toIntConstant(rv));
        } else if (rv instanceof Ir.VarRval) {
            return new Arm.Operand2Reg(toReg(rv));
        } else {
            throw new AssertionError("invalid rval: " + rv);
        }
    }

    private String genLabel() {
        return ".L" + (labelIdx++);
    }

    public static int toIntConstant(Ir.Rval rv) {
        if (rv instanceof Ir.IntLitRval) {
            return ((Ir.IntLitRval) rv).i;
        } else if (rv instanceof Ir.BoolLitRval) {
            return ((Ir.BoolLitRval) rv).b ? 1 : 0;
        } else if (rv instanceof Ir.NullLitRval) {
            return 0;
        } else {
            throw new AssertionError("invalid rval: " + rv);
        }
    }

    private void doParallelCopies(List<ParallelCopy> copies) {
        // Parallel copy sequentialization algorithm
        // https://hal.inria.fr/inria-00349925v1/document

        HashSet<Arm.Reg> freeRegs = new HashSet<>(calleeRegs);
        freeRegs.add(Arm.Reg.R0);
        freeRegs.add(Arm.Reg.R1);
        freeRegs.add(Arm.Reg.R2);
        freeRegs.add(Arm.Reg.R3);

        HashSet<Arm.Reg> todo = new HashSet<>(); // Set of registers in the graph
        HashSet<Arm.Reg> isDst = new HashSet<>(); // Set of registers whose results will be used.
        for (ParallelCopy cp : copies) {
            freeRegs.remove(cp.src);
            freeRegs.remove(cp.dst);
            todo.add(cp.src);
            todo.add(cp.dst);
            isDst.add(cp.dst);
        }

        HashMap<Arm.Reg, Arm.Reg> pred = new HashMap<>();
        HashMap<Arm.Reg, HashSet<Arm.Reg>> succs = new HashMap<>();
        for (Arm.Reg reg : todo) {
            pred.put(reg, null);
            succs.put(reg, new HashSet<>());
        }
        for (Arm.Reg reg : freeRegs) {
            pred.put(reg, null);
            succs.put(reg, new HashSet<>());
        }

        for (ParallelCopy cp : copies) {
            if (pred.get(cp.dst) != null)
                throw new AssertionError("BUG: multiple incoming edges to a single register: " + copies);
            pred.put(cp.dst, cp.src);
            succs.get(cp.src).add(cp.dst);
        }

        ArrayList<Arm.Reg> ready = new ArrayList<>();
        for (Arm.Reg reg : todo) {
            if (succs.get(reg).isEmpty())
                ready.add(reg);
        }

        boolean usedLr = false;
        while (!todo.isEmpty()) {
            if (!ready.isEmpty()) {
                Arm.Reg dst = ready.remove(ready.size() - 1);
                Arm.Reg src = Objects.requireNonNull(pred.get(dst));
                emitAssign(dst, src);

                // Remove edge
                succs.get(src).remove(dst);
                pred.put(dst, null);

                // Transfer all remaining outgoing edges from src to dst (except for src->src self-loops)
                boolean srcHasSelfLoop = succs.get(src).contains(src);
                for (Arm.Reg r : succs.get(src)) {
                    if (r != src) {
                        succs.get(dst).add(r);
                        pred.put(r, dst);
                    }
                }
                succs.get(src).clear();
                if (srcHasSelfLoop)
                    succs.get(src).add(src);

                if (pred.get(src) != null) {
                    ready.add(src);
                } else {
                    todo.remove(src);
                    if (!isDst.contains(src))
                        freeRegs.add(src);
                }

                if (succs.get(dst).isEmpty())
                    todo.remove(dst);
            } else {
                // Need to break the cycle. Select one node.
                Arm.Reg a = todo.iterator().next();
                if (pred.get(a) == null)
                    throw new AssertionError("wat: " + a + ", " + pred + ", " + copies);
                Arm.Reg b = Objects.requireNonNull(pred.get(a));

                if (a == b) {
                    // Self-loop. Skip it
                    todo.remove(a);
                    continue;
                }

                // Select a free Register
                Arm.Reg tmpReg;
                if (freeRegs.isEmpty()) {
                    tmpReg = Arm.Reg.LR;
                    pred.put(tmpReg, null);
                    succs.put(tmpReg, new HashSet<>());
                    currBlock.instrs.add(new Arm.PushInstr(Arrays.asList(Arm.Reg.LR)));
                    usedLr = true;
                } else {
                    tmpReg = freeRegs.iterator().next();
                    freeRegs.remove(b);
                }
                todo.add(tmpReg);

                // Break the cycle
                emitAssign(tmpReg, b);
                // Edge from tmpReg -> a
                pred.put(a, tmpReg);
                succs.get(tmpReg).add(a);
                // Remove b -> a edge, b is now ready
                succs.get(b).remove(a);
                ready.add(b);
            }
        }

        if (usedLr)
            currBlock.instrs.add(new Arm.PopInstr(Arrays.asList(Arm.Reg.LR)));
    }

    private static class ParallelCopy {
        private final Arm.Reg src;
        private final Arm.Reg dst;

        private ParallelCopy(Arm.Reg src, Arm.Reg dst) {
            this.src = src;
            this.dst = dst;
        }

        @Override
        public String toString() {
            return "(" + src + ", " + dst + ")";
        }
    }

    private static String methNameToLabel(String x) {
        if (x.equals("main"))
            return "main";
        return "." + x.replace("%", "__");
    }
}
