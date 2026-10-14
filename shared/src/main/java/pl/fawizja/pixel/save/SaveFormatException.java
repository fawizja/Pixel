package pl.fawizja.pixel.save;

/** Zapis jest uszkodzony, niekompletny albo ma nieobslugiwana wersje. */
public class SaveFormatException extends Exception {
    public SaveFormatException(String message) { super(message); }
    public SaveFormatException(String message, Throwable cause) { super(message, cause); }
}
