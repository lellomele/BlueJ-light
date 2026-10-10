/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.util.List;
import java.util.Locale;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public final class ErrorExplanations
{
    public record Explanation(String concept, List<String> hints) {}
    public static Explanation explain(String code, Locale locale)
    {
        boolean it = locale.getLanguage().equals("it");
        String key = code == null ? "" : code;
        if (key.contains("invalid.meth.decl.ret.type.req")) return item(it,
            "Missing method return type", "Tipo di ritorno del metodo mancante",
            "A method must declare its return type before its name. Use void only when it returns no value. For example: public int answer() { return 42; }",
            "Prima del nome del metodo manca il tipo di ritorno. Usa void solo se non restituisce un valore. Esempio: public int answer() { return 42; }",
            "Only a constructor has no return type, and its name must exactly match the class name. Choose the type based on the value returned; do not add void to a method that returns a value.",
            "Solo un costruttore non ha tipo di ritorno, e il suo nome deve coincidere con quello della classe. Scegli il tipo in base al valore restituito: non aggiungere void a un metodo che restituisce un valore.");
        if (key.contains("cant.resolve")) return item(it,
            "Names and visibility", "Nomi e visibilit\u00e0",
            "Java cannot find this name in the current scope.", "Java non trova questo nome nell'ambito corrente.",
            "Check spelling and upper/lower case, then the declaration, package and imports.", "Controlla maiuscole e minuscole, poi dichiarazione, package e import.");
        if (key.contains("prob.found.req") || key.contains("incompatible.types")) return item(it,
            "Incompatible types", "Tipi incompatibili",
            "The expression type does not match the type required here.", "Il tipo dell'espressione non corrisponde al tipo richiesto.",
            "Compare both types in the original message. A cast is not always a valid conversion: for example, Integer.parseInt parses text.", "Confronta i due tipi nel messaggio originale. Un cast non \u00e8 sempre una conversione valida: Integer.parseInt, per esempio, interpreta un testo.");
        if (key.contains("cant.apply")) return item(it,
            "Method parameters", "Parametri del metodo",
            "The arguments do not match an available method or constructor.", "Gli argomenti non corrispondono a un metodo o costruttore disponibile.",
            "Compare number, order and types of arguments with the declared parameters.", "Confronta numero, ordine e tipi degli argomenti con i parametri dichiarati.");
        if (key.contains("non-static.cant.be.ref")) return item(it,
            "Objects and static members", "Oggetti e membri statici",
            "An instance member needs an object; a static context has no implicit this object.", "Un membro di istanza richiede un oggetto; in un contesto statico non esiste un oggetto this implicito.",
            "Ask which object owns this member. Do not add static just to hide the error.", "Individua l'oggetto a cui appartiene il membro. Non aggiungere static soltanto per nascondere l'errore.");
        if (key.contains("unreported.exception")) return item(it,
            "Checked exceptions", "Eccezioni controllate",
            "This operation may throw a checked exception.", "Questa operazione pu\u00f2 generare un'eccezione controllata.",
            "Choose whether this method can handle the failure with try/catch or should declare it with throws.", "Decidi se il metodo pu\u00f2 gestire il problema con try/catch oppure deve dichiarare l'eccezione con throws.");
        if (key.contains("missing.ret.stmt")) return item(it,
            "Return paths", "Percorsi di ritorno",
            "A method with a non-void result must return a value on every normally completed path.", "Un metodo con risultato non void deve restituire un valore in ogni percorso che termina normalmente.",
            "Trace the if/else and loop paths, including the path where a condition is false.", "Segui i percorsi di if/else e cicli, anche quando una condizione \u00e8 falsa.");
        if (key.contains("var.might.not.have.been.initialized")) return item(it,
            "Definite assignment", "Inizializzazione delle variabili",
            "A local variable may be read before it has been assigned a value.", "Una variabile locale potrebbe essere letta prima di ricevere un valore.",
            "Trace each branch between declaration and use. Assign a meaningful value on every path.", "Segui ogni ramo tra dichiarazione e uso. Assegna un valore significativo in tutti i percorsi.");
        if (key.contains("expected") || key.contains("premature.eof") || key.contains("illegal.start")) return item(it,
            "Java syntax", "Sintassi Java",
            "The parser could not complete a Java construct. The cause may be just before the highlighted position.", "Il parser non riesce a completare un costrutto Java. La causa potrebbe precedere il punto evidenziato.",
            "Check matching parentheses and braces, statement semicolons and the first compiler error before changing later lines.", "Controlla parentesi, graffe, punti e virgola e il primo errore del compilatore prima di modificare le righe successive.");
        if (key.contains("already.defined") || key.contains("duplicate")) return item(it,
            "Unique declarations", "Dichiarazioni univoche",
            "Two declarations conflict in the same scope.", "Due dichiarazioni sono in conflitto nello stesso ambito.",
            "Compare their names and, for methods, parameter types. A different return type alone does not create an overload.", "Confronta nomi e, per i metodi, tipi dei parametri. Il solo tipo di ritorno diverso non crea un overload.");
        return item(it, "Reading compiler diagnostics", "Leggere i messaggi del compilatore",
            "There is no specific explanation for this diagnostic. The original compiler message remains authoritative.", "Non \u00e8 disponibile una spiegazione specifica. Il messaggio originale del compilatore resta il riferimento.",
            "Start with the first error. Inspect the indicated line and the preceding construct; recompile after one focused change.", "Parti dal primo errore. Esamina la riga indicata e il costrutto precedente; ricompila dopo una modifica mirata.");
    }

    private static Explanation item(boolean it, String en, String italian, String en1, String it1, String en2, String it2)
    { return new Explanation(it ? italian : en, List.of(it ? it1 : en1, it ? it2 : en2)); }
}
