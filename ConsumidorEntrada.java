
public interface ConsumidorEntrada {
    /**
     * Recibe un par (clave, posicion) entregado por la consulta por rango.
     * 
     * @param clave    Clave de la entrada en el árbol.
     * @param posicion Posición física del registro en datos.dat.
     */
    void aceptar(long clave, long posicion);
}