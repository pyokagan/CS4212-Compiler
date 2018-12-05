package pyokagan.cs4212;

import java_cup.runtime.ComplexSymbolFactory;
import java_cup.runtime.Symbol;

public class MySymbolFactory extends ComplexSymbolFactory {
    @Override
    public Symbol newSymbol(String name, int id, Location left, Location right, Object value) {
        ComplexSymbol sym = (ComplexSymbol) super.newSymbol(name, id, left, right, value);
        if (value instanceof Ast.Locatable) {
            Ast.Locatable locatable = (Ast.Locatable) value;
            locatable.setLocation(new Ast.Location(sym.getLeft().getLine(), sym.getLeft().getColumn()));
        }
        return sym;
    }

    @Override
    public Symbol newSymbol(String name, int id, Symbol left, Object value) {
        ComplexSymbol sym = (ComplexSymbol) super.newSymbol(name, id, left, value);
        if (value instanceof Ast.Locatable) {
            Ast.Locatable locatable = (Ast.Locatable) value;
            locatable.setLocation(new Ast.Location(sym.getLeft().getLine(), sym.getLeft().getColumn()));
        }
        return sym;
    }

    @Override
    public Symbol newSymbol(String name, int id, Symbol left, Symbol right, Object value) {
        ComplexSymbol sym = (ComplexSymbol) super.newSymbol(name, id, left, right, value);
        if (value instanceof Ast.Locatable) {
            Ast.Locatable locatable = (Ast.Locatable) value;
            locatable.setLocation(new Ast.Location(sym.getLeft().getLine(), sym.getLeft().getColumn()));
        }
        return sym;
    }

}
