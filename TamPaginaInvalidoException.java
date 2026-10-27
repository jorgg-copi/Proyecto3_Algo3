public class TamPaginaInvalidoException extends RuntimeException {

    public TamPaginaInvalidoException() {
        super("los parametros L y M no son validos");
    }
}
