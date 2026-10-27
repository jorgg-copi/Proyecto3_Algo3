public class FormatoInvalidoException extends RuntimeException {

    public FormatoInvalidoException() {
        super("formato invalido");
    }

    public FormatoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
