package pyokagan.cs4212;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.*;
import javax.swing.*;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

public class Main {
    // Examples
    private static String EXAMPLE_HELLO_WORLD = "class Main {\n"
            + "    Void main() {\n"
            + "        println(\"Hello World!\");\n"
            + "    }\n"
            + "}\n";

    private static String EXAMPLE_LINKEDLIST = "// Linked list\n"
        + "\n"
        + "class MainC {\n"
        + "    Void main() {\n"
        + "        IntList lst;\n"
        + "\n"
        + "        lst = new IntList();\n"
        + "        lst.init();\n"
        + "        lst.insert(10);\n"
        + "        lst.insert(8);\n"
        + "        lst.insert(6);\n"
        + "        lst.insert(2);\n"
        + "        lst.print();\n"
        + "    }\n"
        + "}\n"
        + "\n"
        + "class IntNode {\n"
        + "    Int data;\n"
        + "    IntNode next;\n"
        + "}\n"
        + "\n"
        + "class IntList {\n"
        + "    IntNode first;\n"
        + "\n"
        + "    Void init() {\n"
        + "        first = null;\n"
        + "    }\n"
        + "\n"
        + "    Void insert(Int data) {\n"
        + "        IntNode newNode;\n"
        + "\n"
        + "        newNode = new IntNode();\n"
        + "        newNode.data = data;\n"
        + "        newNode.next = first;\n"
        + "        first = newNode;\n"
        + "    }\n"
        + "\n"
        + "    Void print() {\n"
        + "        IntNode node;\n"
        + "\n"
        + "        node = first;\n"
        + "        while (node != null) {\n"
        + "            println(node.data);\n"
        + "            node = node.next;\n"
        + "        }\n"
        + "    }\n"
        + "}\n";

    private static String EXAMPLE_FIZZBUZZ = "// FizzBuzz\n"
                    + "\n"
                    + "class Main {\n"
                    + "    Void main() {\n"
                    + "        FizzBuzz fb;\n"
                    + "\n"
                    + "        fb = new FizzBuzz();\n"
                    + "        fb.run();\n"
                    + "    }\n"
                    + "}\n"
                    + "\n"
                    + "class FizzBuzz {\n"
                    + "    Void run() {\n"
                    + "        Int x;\n"
                    + "        x = 1;\n"
                    + "        while (x <= 100) {\n"
                    + "            if (isDivisibleBy(x, 15)) {\n"
                    + "                println(\"FizzBuzz\");\n"
                    + "            } else {\n"
                    + "                if (isDivisibleBy(x, 3)) {\n"
                    + "                    println(\"Fizz\");\n"
                    + "                } else {\n"
                    + "                    if (isDivisibleBy(x, 5)) {\n"
                    + "                        println(\"Buzz\");\n"
                    + "                    } else {\n"
                    + "                        println(x);\n"
                    + "                    }\n"
                    + "                }\n"
                    + "            }\n"
                    + "            x = x + 1;\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    Bool isDivisibleBy(Int x, Int y) {\n"
                    + "        Int quotient;\n"
                    + "\n"
                    + "        quotient = x / y;\n"
                    + "        return quotient * y == x;\n"
                    + "    }\n"
                    + "}\n";

    private static final String EXAMPLE_LOTSOFARGS = "// Under the ARM calling convention,\n"
                    + "// first 4 arguments must be in registers r0-r3,\n"
                    + "// the 5th argument and above must be on the stack.\n"
                    + "// Take a look at the STACKARG stmts created at IrLowerPass.\n"
                    + "\n"
                    + "class Main {\n"
                    + "    Void main() {\n"
                    + "        LotsOfArgs foo;\n"
                    + "\n"
                    + "        foo = new LotsOfArgs();\n"
                    + "        println(\"Expected: prints 137\");\n"
                    + "        println(foo.f(3, 7, 1, 4, 2, 6)); // 137\n"
                    + "    }\n"
                    + "}\n"
                    + "\n"
                    + "class LotsOfArgs {\n"
                    + "    Int f(Int a, Int b, Int c, Int d, Int e, Int f) {\n"
                    + "        return a + b + c + d + (20 * f) + e;\n"
                    + "    }\n"
                    + "}\n";

    private static final String EXAMPLE_NESTED = "class MainC {\n"
                    + "    Void main() {\n"
                    + "        Int i;\n"
                    + "        Int j;\n"
                    + "\n"
                    + "        i = 0;\n"
                    + "        while (i <= 10) {\n"
                    + "            j = 0;\n"
                    + "            while (j < i) {\n"
                    + "                println(j);\n"
                    + "                j = j + 1;\n"
                    + "            }\n"
                    + "            i = i + 1;\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";

    private static final String EXAMPLE_SPILL =
            "// In this program, some variables will need to be spilled\n"
                    + "// as register pressure is too high.\n"
                    + "// Take note of how some SPILL and RELOAD stmts appear after SpillPass,\n"
                    + "// and how some PHI nodes are converted into PHIMEM nodes.\n"
                    + "\n"
                    + "class Main {\n"
                    + "    Void main() {\n"
                    + "        Int x;\n"
                    + "        Int i1;\n"
                    + "        Int i2;\n"
                    + "        Int i3;\n"
                    + "        Int i4;\n"
                    + "        Int i5;\n"
                    + "        Int i6;\n"
                    + "        Int i7;\n"
                    + "        Int i8;\n"
                    + "        Int i9;\n"
                    + "        Int i10;\n"
                    + "        Int i11;\n"
                    + "        Int i12;\n"
                    + "        Int i13;\n"
                    + "        Int i14;\n"
                    + "\n"
                    + "        // if x > 42, prints 705. Otherwise, prints 563.\n"
                    + "        x = 43;\n"
                    + "\n"
                    + "        if (x > 42) {\n"
                    + "            i1 = 45;\n"
                    + "            i2 = 2;\n"
                    + "            i3 = 9;\n"
                    + "            i4 = 80;\n"
                    + "            i5 = 25;\n"
                    + "            i6 = 81;\n"
                    + "            i7 = 43;\n"
                    + "            i8 = 19;\n"
                    + "            i9 = 45;\n"
                    + "            i10 = 143;\n"
                    + "            i11 = 87;\n"
                    + "            i12 = 47;\n"
                    + "            i13 = 73;\n"
                    + "            i14 = 6;\n"
                    + "        } else {\n"
                    + "            i14 = 90;\n"
                    + "            i13 = 82;\n"
                    + "            i12 = 24;\n"
                    + "            i11 = 6;\n"
                    + "            i10 = 61;\n"
                    + "            i9 = 3;\n"
                    + "            i8 = 24;\n"
                    + "            i7 = 62;\n"
                    + "            i6 = 51;\n"
                    + "            i5 = 28;\n"
                    + "            i4 = 7;\n"
                    + "            i3 = 72;\n"
                    + "            i2 = 9;\n"
                    + "            i1 = 44;\n"
                    + "        }\n"
                    + "\n"
                    + "        x = i1 + i2 + i3 + i4 + i5 + i6 + i7 + i8 + i9 + i10 + i11 + i12 + i13 + i14;\n"
                    + "\n"
                    + "        println(x);\n"
                    + "    }\n"
                    + "}\n";

    private static final String EXAMPLE_FACT = "class Main {\n"
                    + "    Void main () {\n"
                    + "        Facto f;\n"
                    + "        Int res;\n"
                    + "\n"
                    + "        f = new Facto();\n"
                    + "        res = f.fact(10);\n"
                    + "        println(\"Should print 3628800:\");\n"
                    + "        println(res);\n"
                    + "    }\n"
                    + "}\n"
                    + "\n"
                    + "class Facto {\n"
                    + "    Int fact (Int n) {\n"
                    + "        Facto f;\n"
                    + "\n"
                    + "        if (n <= 1) {\n"
                    + "            return 1;\n"
                    + "        } else {\n"
                    + "            f = new Facto();\n"
                    + "            return n * f.fact(n - 1);\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";

    private final JFrame frame;
    private final JMenuBar menuBar;

    private final JSplitPane mainPanel;
    private final JPanel editorPanel;
    private final JPanel compilePanel;
    private final JCheckBox optimizeCheckbox;
    private final JLabel statusLabel;
    private final RSyntaxTextArea editorTextArea;
    private final JButton compileButton;
    private final JPanel resultPanel;
    private final RSyntaxTextArea resultTextArea;

    // Results
    private String[] results = new String[] { "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "" };
    private static final int AST_RESULT = 0;
    private static final int IR_GEN_RESULT = 1;
    private static final int FLOW_PASS_RESULT = 2;
    private static final int SSA_PASS_RESULT = 3;
    private static final int CRIT_EDGE_PASS_RESULT = 4;
    private static final int IR_LOWER_PASS_RESULT = 5;
    private static final int PHI_WEB_PASS_RESULT = 6;
    private static final int SPILL_PASS_RESULT = 7;
    private static final int REG_TARGET_PASS_RESULT = 8;
    private static final int COLOR_PASS_RESULT = 9;
    private static final int ARM_GEN_RESULT = 10;
    private static final int ARM_JUMP_OPT_RESULT = 11;
    private static final int ARM_DEAD_BLOCK_ELIM_RESULT = 12;
    private static final int ARM_FALLTHRU_OPT_RESULT = 13;
    private static final int ARM_LABEL_OPT_RESULT = 14;
    private static final int ARM_RUNTIME_RESULT = 15;

    private Main() {
        frame = new JFrame("JLite Compiler");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        String[] strs = {"AST", "IrGen", "FlowPass", "SsaPass", "CritEdgePass", "IrLowerPass", "PhiWebPass",
                "SpillPass", "RegTargetPass", "ColorPass", "ArmGen", "ArmJumpOpt",
                "ArmDeadBlockElim", "ArmFallthruOpt", "ArmLabelOpt", "ArmRuntime"};
        JList list = new JList(strs);
        // Result Panel
        resultPanel = new JPanel();
        resultPanel.setLayout(new BoxLayout(resultPanel, BoxLayout.LINE_AXIS));
        resultTextArea = new RSyntaxTextArea(20, 60);
        resultTextArea.setEditable(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting())
                return;
            if (list.getSelectedIndex() < 0) {
                resultTextArea.setText("");
                return;
            }
            int idx = list.getSelectedIndex();
            resultTextArea.setText(results[idx]);
        });
        JScrollPane listScroller = new JScrollPane(list);
        listScroller.setMaximumSize(new Dimension(200, Short.MAX_VALUE));
        listScroller.setPreferredSize(new Dimension(200, 80));
        resultPanel.add(listScroller);
        RTextScrollPane sp2 = new RTextScrollPane(resultTextArea);
        resultPanel.add(sp2);

        // Editor panel
        editorTextArea = new RSyntaxTextArea(20, 60);
        RTextScrollPane sp = new RTextScrollPane(editorTextArea);
        editorTextArea.setText(EXAMPLE_HELLO_WORLD);
        editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        editorPanel.add(sp);
        compilePanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
        statusLabel = new JLabel("");
        compileButton = new JButton("Compile");
        optimizeCheckbox = new JCheckBox("Optimize");
        optimizeCheckbox.addActionListener(action -> {
            statusLabel.setText("");
        });
        compileButton.addActionListener(action -> {
            if (run()) {
                statusLabel.setText("Compile successful.");
                statusLabel.setForeground(Color.BLACK);
            } else {
                statusLabel.setText("Compile failed.");
                statusLabel.setForeground(Color.RED);
            }
            if (list.getSelectedIndex() < 0)
                list.setSelectedIndex(0);
            resultTextArea.setText(results[list.getSelectedIndex()]);
        });
        compilePanel.add(compileButton);
        compilePanel.add(optimizeCheckbox);
        compilePanel.add(statusLabel);
        editorPanel.add(compilePanel, BorderLayout.PAGE_END);

        // Main Panel
        mainPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, resultPanel);
        frame.setContentPane(mainPanel);

        // Menubar
        menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        JMenu newExampleMenu = new JMenu("New from Example");
        fileMenu.add(newExampleMenu);
        JMenuItem item = new JMenuItem("Hello World");
        item.addActionListener(action -> editorTextArea.setText(EXAMPLE_HELLO_WORLD));
        newExampleMenu.add(item);
        item = new JMenuItem("Linked list");
        item.addActionListener(action -> editorTextArea.setText(EXAMPLE_LINKEDLIST));
        newExampleMenu.add(item);
        item = new JMenuItem("FizzBuzz");
        item.addActionListener(action -> editorTextArea.setText(EXAMPLE_FIZZBUZZ));
        newExampleMenu.add(item);
        item = new JMenuItem("Lots of args");
        item.addActionListener(action -> editorTextArea.setText(EXAMPLE_LOTSOFARGS));
        newExampleMenu.add(item);
        item = new JMenuItem("Nested loops");
        item.addActionListener(action -> editorTextArea.setText(EXAMPLE_NESTED));
        newExampleMenu.add(item);
        item = new JMenuItem("Spill");
        item.addActionListener(action -> editorTextArea.setText(EXAMPLE_SPILL));
        newExampleMenu.add(item);
        item = new JMenuItem("Factorial");
        item.addActionListener(action -> editorTextArea.setText(EXAMPLE_FACT));
        newExampleMenu.add(item);

        // Display the window
        frame.setJMenuBar(menuBar);
        frame.pack();
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
    }

    private boolean run() {
        final Ast.Prog prog;
        boolean optimize = optimizeCheckbox.isSelected();
        StringReader reader = new StringReader(editorTextArea.getText());
        try {
            prog = Parser.parse(reader);
        } catch (Exception e) {
            String x = "Failed to parse input.\n\n" + e.getMessage();
            for (int i = 0; i < results.length; i++)
                results[i] = x;
            return false;
        }
        results[AST_RESULT] = prog.render(0, 0);
        try {
            StaticCheck.run(prog);
        } catch (StaticCheck.SemErrors e) {
            StringBuilder sb = new StringBuilder();
            sb.append("Type checking failed.\n\n");
            for (StaticCheck.SemError err : e.getErrors())
                sb.append("error:").append(err.location).append(": ").append(err.getMessage()).append('\n');
            String x = sb.toString();
            for (int i = AST_RESULT + 1; i < results.length; i++)
                results[i] = x;
            return false;
        }
        Ir.Prog irProg = IrGen.run(prog);
        results[IR_GEN_RESULT] = irProg.render(0);
        for (int i = IR_GEN_RESULT + 1; i < results.length; i++)
            results[i] = "";
        for (Ir.Meth meth : irProg.meths) {
            FlowPass.run(meth);
            results[FLOW_PASS_RESULT] += meth.render(0);
            DomPass domPass = new DomPass(meth);
            DomFrontierPass domFrontierPass = new DomFrontierPass(meth, domPass);
            SsaPass.run(meth, domPass, domFrontierPass);
            results[SSA_PASS_RESULT] += meth.render(0);
            if (CritEdgePass.run(meth)) {
                // CFG modified, need to recompute dom info
                domPass = new DomPass(meth);
                domFrontierPass = new DomFrontierPass(meth, domPass);
            }
            results[CRIT_EDGE_PASS_RESULT] += meth.render(0);
            IrLowerPass.run(meth);
            results[IR_LOWER_PASS_RESULT] += meth.render(0);
            PhiWebPass.run(meth);
            results[PHI_WEB_PASS_RESULT] += meth.render(0);
            SpillPass.run(meth, domPass, domFrontierPass);
            results[SPILL_PASS_RESULT] += meth.render(0);
            RegTargetPass.run(meth, domPass, domFrontierPass);
            results[REG_TARGET_PASS_RESULT] += meth.render(0);
            LivePass livePass = new LivePass(meth);
            ColorPass.run(meth, domPass, livePass);
            results[COLOR_PASS_RESULT] += meth.render(0);
        }
        Arm.Prog armProg = ArmGen.run(irProg);
        results[ARM_GEN_RESULT] = armProg.render();
        if (optimize) {
            ArmJumpOpt.run(armProg);
            results[ARM_JUMP_OPT_RESULT] = armProg.render();
            ArmDeadBlockElim.run(armProg);
            results[ARM_DEAD_BLOCK_ELIM_RESULT] = armProg.render();
            ArmFallthruOpt.run(armProg);
            results[ARM_FALLTHRU_OPT_RESULT] = armProg.render();
            ArmLabelOpt.run(armProg);
            results[ARM_LABEL_OPT_RESULT] = armProg.render();
        } else {
            String msg = "Optimization not enabled.";
            results[ARM_JUMP_OPT_RESULT] = msg;
            results[ARM_DEAD_BLOCK_ELIM_RESULT] = msg;
            results[ARM_FALLTHRU_OPT_RESULT] = msg;
            results[ARM_LABEL_OPT_RESULT] = msg;
        }
        ArmRuntime.run(armProg);
        results[ARM_RUNTIME_RESULT] = armProg.render();
        return true;
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            new Main();
        });
    }

}
