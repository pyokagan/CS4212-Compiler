package pyokagan.cs4212;

import java.util.*;

/**
 * Assign vars to phi webs.
 * This should only be run on Conventional-SSA form.
 * https://pdfs.semanticscholar.org/b4e0/f3301cffb358e836ee2964a0316e1b263974.pdf
 */
public class PhiWebPass {
    private Ir.Meth meth;

    // For phi-web discovery algorithm, union-find
    private HashMap<Ir.Var, Ir.Var> P;
    private HashMap<Ir.Var, Integer> RANK;

    private HashMap<Ir.Var, Ir.PhiWeb> parentToPhiWeb = new HashMap<>();
    private ArrayList<Ir.PhiWeb> phiWebs = new ArrayList<>();

    private PhiWebPass(Ir.Meth meth) {
        this.meth = meth;
        phiWeb();

        for (Ir.Var v : meth.args)
            doVar(v);
        for (Ir.Var v : meth.locals)
            doVar(v);
        meth.phiWebs = phiWebs;
    }

    public static void run(Ir.Meth meth) {
        new PhiWebPass(meth);
    }

    public static void run(Ir.Prog prog) {
        for (Ir.Meth meth : prog.meths)
            run(meth);
    }

    private void doVar(Ir.Var v) {
        Ir.Var parent = phiWebFindSet(v);
        Ir.PhiWeb phiWeb;
        if (parentToPhiWeb.containsKey(parent)) {
            phiWeb = parentToPhiWeb.get(parent);
        } else {
            phiWeb = new Ir.PhiWeb();
            phiWebs.add(phiWeb);
            parentToPhiWeb.put(parent, phiWeb);
        }
        v.phiWeb = phiWeb;
    }

    private void phiWeb() {
        P = new HashMap<>();
        RANK = new HashMap<>();

        for (Ir.Var v : meth.args) {
            P.put(v, v);
            RANK.put(v, 0);
        }
        for (Ir.Var v : meth.locals) {
            P.put(v, v);
            RANK.put(v, 0);
        }

        for (Ir.Block block : meth.blocks) {
            for (Ir.Stmt stmt : block.stmts) {
                if (stmt instanceof Ir.PhiStmt) {
                    Ir.PhiStmt phiStmt = (Ir.PhiStmt) stmt;

                    for (Ir.Var v : phiStmt.args) {
                        if (v == null)
                            continue;
                        phiWebUnion(phiStmt.getDst(), v);
                    }
                }
            }
        }
    }

    private Ir.Var phiWebFindSet(Ir.Var x) {
        if (P.get(x) != x)
            P.put(x, phiWebFindSet(P.get(x)));
        return P.get(x);
    }

    private void phiWebLink(Ir.Var x, Ir.Var y) {
        if (RANK.get(x) > RANK.get(y)) {
            P.put(y, x);
        } else {
            P.put(x, y);
        }
        if (RANK.get(x) == RANK.get(y))
            RANK.put(y, RANK.get(y) + 1);
    }

    private void phiWebUnion(Ir.Var x, Ir.Var y) {
        x = phiWebFindSet(x);
        y = phiWebFindSet(y);
        if (x != y)
            phiWebLink(x, y);
    }
}
