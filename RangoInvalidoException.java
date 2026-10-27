public class RangoInvalidoException extends RuntimeException {
    public RangoInvalidoException() {
        super("Rango invalido: 'desde' no puede ser mayor que 'hasta'.");
    }

    public RangoInvalidoException(String mensaje) {
        super(mensaje);
    }
}