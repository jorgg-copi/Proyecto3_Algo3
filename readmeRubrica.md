g28 - Jorge Hermosilla 5727706 

# 1.Capacidad de página
## Para hallar L y M de P 

Siendo que **P es el numero de bytes de una pagina** , **M el numero maximo de hijos de una pagina nodo interno** y **L el numero maximo de pares clave-posicion de una pagina hoja** y para cada pagina los **12 primeros bytes estan reservados para los datos de la pagina** 

**M** = Partiendo de que cada hijo representa un entero de 4 bytes (numero de la pagina que corresponde a ese hijo) y cada clave un tipo long de 8 bytes , y en cada pagina nodo para **L hijos** hay **L-1 claves**  debermos hallar un numero que **L*4 + (L-1)*8 <= P-12** despejando L nos queda **L=(P-4)/12**.

**L** = Partiendo de que cada par clave-posicion cuesta 16 bytes (clave long de 8 bytes + posicion long de 8 bytes )  , para hallar el numero maximo de pares que entran en P-12 bytes es **(P-12)/16**

## Por que L y M son distintos 

Se debe principalmente por la diferencia de costos en bytes , y a la estructura de cada tipo hijo o interno ya que en el interno no son datos que vienen en pares como en las hojas sino que hay L hijos y L-1 claves 

---
# 2.Altura

**Parámetros base (P = 4096 bytes, N = 300.000 claves):**
- L = 255 pares por hoja
- M = 341 hijos por nodo interno

### Cota Mínima (Páginas al 100% de ocupación)
- Cantidad de hojas necesarias: 300.000 / 255 = 1.177 hojas.
- Con M = 341, la raíz puede tener hasta 341 hijos, y el nivel 2 puede tener hasta 341 * 341 = 116.281 hojas.
- Como 1.177 hojas es menor que 116.281, la estructura se completa en **3 niveles**.
- Fórmula: 1 + ceil(log341(1.177)) = 3 niveles.

### Cota Máxima (Mínima ocupación según Invariante I3)
- Mínimo de claves por hoja: ceil(255 / 2) = 128 claves.
- Mínimo de hijos por interno: ceil(341 / 2) = 171 hijos.
- Hojas necesarias en el peor caso: 300.000 / 128 = 2.344 hojas.
- En el caso teórico extremo (una raíz desbalanceada con solo 2 hijos), la cota máxima teórica requeriría hasta **4 niveles**.
- Sin embargo, en la práctica (Tabla B), para conectar las 2.344 hojas se necesitan solo 14 nodos internos en el nivel 2 (2.344 / 171 = 14). Como la raíz soporta hasta 341 hijos, esos 14 nodos entran en la raíz sin dividirse. Por eso, la altura real en el experimento se mantiene en **3 niveles**.

### Comparación con las Tablas y el ABB
- En la **Tabla B** (N = 300.000, P = 4096), la altura medida experimentalmente fue de **3 niveles** tanto para la inserción en orden como para la aleatoria.
- Un **Árbol de Búsqueda Binaria (ABB)** perfectamente balanceado para N = 300.000 claves tiene una altura de ceil(log2(300.001)) = **19 niveles**.
- Si cada nodo del ABB estuviera en una página de disco separada, buscar una clave requeriría **19 lecturas de página**. En el Árbol B+, esa misma búsqueda requiere solo **3 lecturas de página**.

### Conclusión
La alta capacidad de los nodos internos del Árbol B+ (M = 341) permite aplanar la estructura de 19 niveles en un ABB a solo 3 niveles en disco. Esto reduce los accesos a disco por búsqueda de 19 a 3 lecturas de página, demostrando la enorme ventaja de rendimiento que ofrecen los árboles B+ para motores de bases de datos.
### Tabla de Referencia 

                               TABLA A: ESTRUCTURA (SINTÉTICA, N = 1000000)

+------+-----+-----+-----------------------------------------+-----------------------------------------+
|      |     |     |          INSERCIÓN CRECIENTE            |           INSERCIÓN ALEATORIA           |
|  P   |  M  |  L  +---------+---------+-----------+---------+---------+---------+-----------+---------+
|      |     |     | Niveles |  Hojas  | Ocup.Hojo | Ocup.Int| Niveles |  Hojas  | Ocup.Hojo | Ocup.Int|
+------+-----+-----+---------+---------+-----------+---------+---------+---------+-----------+---------+
|  128 |  10 |   7 |       8 |  250000 |   57,14% |   60,00% |       7 |  196962 |   72,53% |   71,69% |
|  256 |  21 |  15 |       6 |  125000 |   53,33% |   52,39% |       6 |   94394 |   70,63% |   69,88% |
|  512 |  42 |  31 |       5 |   62500 |   51,61% |   52,40% |       5 |   46245 |   69,75% |   67,65% |
| 1024 |  85 |  63 |       4 |   31250 |   50,79% |   50,66% |       4 |   22847 |   69,48% |   62,82% |
| 2048 | 170 | 127 |       4 |   15625 |   50,39% |   50,54% |       3 |   11438 |   68,84% |   58,09% |
| 4096 | 341 | 255 |       3 |    7812 |   50,20% |   50,09% |       3 |    5627 |   69,69% |   57,18% |



                               TABLA B: CONSTRUCCIÓN CON IMDB (N = 300000, P = 4096)

+-----------+----------+---------------+---------------+------+-----+-----+---------+--------+----------+-----------+-----------+----------+------------+
| Modo      | Registros| tam(datos)    | tam(indice)   |  P   |  M  |  L  | Niveles | Hojas  | Internos | Ocup.Hojo | Ocup.Int  | Págs.Escr| Tiempo(ms) |
+-----------+----------+---------------+---------------+------+-----+-----+---------+--------+----------+-----------+-----------+----------+------------+
| En Orden  |   300000 |    25,84 MB |     9,21 MB | 4096 | 341 | 255 |       3 |   2343 |       14 |   50,21% |   49,35% |   304708 |   11367 ms |
| Aleatorio |   300000 |    25,84 MB |     7,09 MB | 4096 | 341 | 255 |       3 |   1805 |        9 |   65,18% |   59,07% |   303622 |   11117 ms |

---
# 3. Entrada/Salida (E/S) por Operación y Complejidad

### Búsqueda: buscar(clave)

#### Lecturas a disco: exactas a niveles()
1. La búsqueda siempre comienza leyendo la página de la raíz (Nivel 1).
2. En cada nodo interno, se realiza una búsqueda binaria en memoria RAM sobre sus claves para determinar por cuál hijo descender.
3. Se desciende exactamente un nivel por cada paso de lectura hasta alcanzar una página hoja en el nivel mas bajo (Nivel niveles()).
4. Por lo tanto, el algoritmo lee exactamente una página por cada nivel del árbol, sumando **niveles() lecturas**.
5. Este costo se mantiene constante **exista o no la clave**, ya que para confirmar que una clave no existe es obligatorio descender hasta la hoja correspondiente.

## Inserción: insertar(clave, posición)

### Lecturas a disco: exactas a niveles()
Para insertar un par (clave, posición), el algoritmo primero realiza la misma navegación descendente desde la raíz hasta la hoja donde corresponde ubicar la clave. Como se lee únicamente una página por cada nivel en el camino de descenso, se realizan exactamente **niveles() lecturas**.

### Escrituras a disco: exactas a 2s + 1
Donde **s** representa la cantidad de divisiones (splits) ocurridas durante la inserción:

1. **Caso sin divisiones (s = 0):** La clave entra en una hoja que tiene espacio libre. Solo se modifica esa hoja y se escribe a disco.
   - Escrituras = 2(0) + 1 = **1 escritura**.

2. **Caso con divisiones (s > 0):**
   - Cada división (ya sea de hoja o de nodo interno) toma un nodo desbordado y lo reparte en dos nodos: el nodo original modificado y un nodo totalmente nuevo. Ambos nodos deben grabarse en disco. Como ocurren **s** divisiones, esto genera **2 * s escrituras**.
   - Al finalizar la propagación hacia arriba de los separadores, la última clave promovida es absorbida por un nodo padre existente o se crea una nueva raíz. En cualquiera de los dos casos, se escribe a disco ese único nodo padre final que absorbió el separador.
   - Sumando ambas partes: **2s + 1 escrituras**.


## Conclusión de Complejidad Algorítmica

### 1. Accesos a disco: Theta(log_M N)
- La altura **h** de un Árbol B+ con capacidad de ramificación **M** y **N** claves almacenadas está acotada por **h = Theta(log_M N)**.
- Dado que tanto `buscar` como `insertar` leen a lo sumo **h** páginas y escriben un número reducido de páginas proporcional a **s** (donde s <= h), la cantidad total de transferencias de páginas entre disco y memoria es de orden **Theta(log_M N)**.

### 2. Comparaciones en memoria RAM: Theta(log N)
- Dentro de cada página de nodo interno se realiza una búsqueda binaria entre sus claves, lo que toma **O(log_2 M)** comparaciones.
- En la hoja se realiza una búsqueda binaria entre sus **L** elementos, lo que toma **O(log_2 L)** comparaciones.
- Al multiplicar las comparaciones internas por la cantidad de niveles del árbol **h**, se obtiene:
  - Comparaciones totales = h * O(log_2 M) = Theta( (log_2 N / log_2 M) * log_2 M ) = **Theta(log N)**.

#### Resumen
El Árbol B+ logra resolver las consultas y las inserciones realizando únicamente **Theta(log_M N) accesos a disco** (minimizando el costo de Entrada/Salida, que es el cuello de botella físico) y **Theta(log N) comparaciones de claves en memoria RAM** (que se ejecutan a velocidad de procesador).

---
# 4.Ocupación de Nodos y Orden de Entrada

## A. Inserción en Orden Creciente y Ocupación del 50%
Cuando las claves se insertan en orden estrictamente creciente, cada nueva clave es mayor que todas las previamente almacenadas. En consecuencia, la inserción siempre ocurre en la última hoja del extremo derecho del árbol.

Cuando esa hoja desborda al alcanzar L + 1 elementos, se realiza una división (split):
- La primera mitad, compuesta por ceil((L + 1) / 2) elementos, se conserva en la hoja izquierda.
- La segunda mitad se traslada a la nueva hoja derecha.

Dado que todas las inserciones futuras continuarán cayendo en la nueva hoja derecha (o en hojas aún más a la derecha), la hoja izquierda recién dividida **nunca vuelve a recibir nuevas claves**. Por lo tanto, todas las hojas anteriores quedan congeladas de manera permanente con una ocupación de exactamente **ceil((L + 1) / 2) elementos**, lo que representa aproximadamente un **50% de su capacidad total (L)**.

## B. Predicción Exacta de Hojas Crecientes para la Tabla A
Sabiendo que cada hoja dividida en orden creciente retiene exactamente **ceil((L + 1) / 2)** elementos, podemos predecir la cantidad total de hojas para N = 1.000.000 claves mediante la fórmula:

Hojas = ceil( N / ceil((L + 1) / 2) )

Aplicando la fórmula para los diferentes tamaños de página P:
- **P = 4096 (L = 255):** Capacidad en split = ceil(256 / 2) = 128 elementos. 
  Hojas = ceil(1.000.000 / 128) = **7.813 hojas**.
- **P = 2048 (L = 127):** Capacidad en split = ceil(128 / 2) = 64 elementos. 
  Hojas = ceil(1.000.000 / 64) = **15.625 hojas**.
- **P = 1024 (L = 63):** Capacidad en split = ceil(64 / 2) = 32 elementos. 
  Hojas = ceil(1.000.000 / 32) = **31.250 hojas**.
- **P = 512 (L = 31):** Capacidad en split = ceil(32 / 2) = 16 elementos. 
  Hojas = ceil(1.000.000 / 16) = **62.500 hojas**.
- **P = 256 (L = 15):** Capacidad en split = ceil(16 / 2) = 8 elementos. 
  Hojas = ceil(1.000.000 / 8) = **125.000 hojas**.
- **P = 128 (L = 7):** Capacidad en split = ceil(8 / 2) = 4 elementos. 
  Hojas = ceil(1.000.000 / 4) = **250.000 hojas**.

## C. Comparación con la Inserción Aleatoria
En la inserción aleatoria, las claves se distribuyen de manera uniforme entre todas las hojas existentes. Cuando una hoja se divide, ambas mitades continúan recibiendo nuevas inserciones con el tiempo. Esto eleva la ocupación promedio de las hojas a aproximadamente un **69% a 70%** .

**Consecuencias de la inserción aleatoria:**
1. **Menor número de hojas:** Requiere cerca de un 30% menos de páginas de hojas en comparación con el orden creciente.
2. **Menor tamaño del índice:** Al requerir menos páginas totales, el archivo `indice.dat` en disco es considerablemente más pequeño.
3. **Reducción de niveles:** En tamaños de página pequeños (como P = 128 o P = 2048), la mayor ocupación permite empaquetar las claves en menos hojas, reduciendo los niveles del árbol.

## D. Orden de Entrada en el Dataset de IMDb
El archivo `title.basics.tsv` viene ordenado de fábrica de manera estrictamente creciente por el identificador `tconst` (`tt0000001`, `tt0000002`, etc.). Por lo tanto, al construir el índice en orden original, el comportamiento en la Tabla B coincide exactamente con el caso creciente: sus hojas quedan en una ocupación cercana al **50%**.

### Tabla de Referencia 
                               TABLA A: ESTRUCTURA (SINTÉTICA, N = 1000000)

+------+-----+-----+-----------------------------------------+-----------------------------------------+
|      |     |     |          INSERCIÓN CRECIENTE            |           INSERCIÓN ALEATORIA           |
|  P   |  M  |  L  +---------+---------+-----------+---------+---------+---------+-----------+---------+
|      |     |     | Niveles |  Hojas  | Ocup.Hojo | Ocup.Int| Niveles |  Hojas  | Ocup.Hojo | Ocup.Int|
+------+-----+-----+---------+---------+-----------+---------+---------+---------+-----------+---------+
|  128 |  10 |   7 |       8 |  250000 |   57,14% |   60,00% |       7 |  196962 |   72,53% |   71,69% |
|  256 |  21 |  15 |       6 |  125000 |   53,33% |   52,39% |       6 |   94394 |   70,63% |   69,88% |
|  512 |  42 |  31 |       5 |   62500 |   51,61% |   52,40% |       5 |   46245 |   69,75% |   67,65% |
| 1024 |  85 |  63 |       4 |   31250 |   50,79% |   50,66% |       4 |   22847 |   69,48% |   62,82% |
| 2048 | 170 | 127 |       4 |   15625 |   50,39% |   50,54% |       3 |   11438 |   68,84% |   58,09% |
| 4096 | 341 | 255 |       3 |    7812 |   50,20% |   50,09% |       3 |    5627 |   69,69% |   57,18% |
-----
# 5. Búsqueda Secuencial contra Índice B+

## A. Análisis de Resultados (Tabla C)

1. **Búsqueda en Árbol B+:**
   - **Clave Existente:** Lee exactamente **niveles() páginas de índice** (3 páginas) para descender hasta la hoja, y realiza **1 lectura de registro** en `datos.dat`.
   - **Clave Inexistente:** Lee exactamente **niveles() páginas de índice** (3 páginas) y determina que la clave no está en la hoja. Realiza **0 lecturas de registro** en `datos.dat`.

2. **Búsqueda Secuencial:**
   - **Clave Existente:** Debe recorrer el archivo `datos.dat` desde el inicio hasta encontrar la coincidencia. Para un dataset de N = 300.000 registros, lee en promedio **N / 2 = 150.000 registros**.
   - **Clave Inexistente:** Debe recorrer el **100% del archivo datos.dat** para confirmar la ausencia de la clave, leyendo los **N = 300.000 registros**.

## B. Crecimiento en Función de N (Complejidad Algorítmica)

- **Búsqueda Secuencial: Theta(N)**
  El costo en lecturas crece de forma lineal con respecto a la cantidad de registros N. Si el archivo triplica su tamaño a 900.000 registros, el costo promedio de la búsqueda secuencial se triplica a 450.000 lecturas.

- **Índice Árbol B+: Theta(log_M N)**
  El costo en lecturas de página crece de forma logarítmica con base M (donde M es el fan-out del nodo interno). Gracias al elevado valor de M (M = 341 para P = 4096), el árbol puede gestionar millones de registros manteniendo una altura fija de 3 a 4 páginas leídas.

### Tabla de referencia 

                               TABLA C: BÚSQUEDAS EXACTAS

+------------------------+-----------+------------------------+------------------------+-----------------+
| Método                 | Consultas | Págs. Índice / Consult | Regs. Leídos / Consult | µs / Consulta   |
+------------------------+-----------+------------------------+------------------------+-----------------+
| B+ Tree (Existentes)   |      1000 |                   3,00 |                   1,00 |           19,26 |
| B+ Tree (Inexistentes) |      1000 |                   3,00 |                   0,00 |           11,05 |
| Secuencial (Existentes) |        50 |                   0,00 |              152493,28 |       747212,80 |
| Secuencial (Inexistentes) |        50 |                   0,00 |              300000,00 |      1460472,69 |

---
# 6. Consultas por Rango

## A. Expresión Formal de Páginas Leídas en un Rango
Para procesar una consulta por rango en el intervalo [desde, hasta] que retorna **s** registros:

1. **Descenso inicial a la primera hoja:** Requiere descender desde la raíz hasta la hoja donde comienza el rango. Esto consume **h - 1 páginas de nodos internos** (donde h es la altura o niveles() del árbol).
2. **Recorrido por la cadena de hojas:** A partir de la primera hoja alcanzada, se recorre la lista enlazada leyendo hojas contiguas. Si la ocupación media de las hojas es **L_ocup** (donde L_ocup = L * ocupacionHojas), la cantidad de páginas de hojas a leer es **ceil(s / L_ocup)**.

**Fórmula total de páginas leídas:**
PaginasLeidas = (h - 1) + ceil(s / L_ocup)

En notación asintótica ajustada, el costo de Entrada/Salida para una consulta por rango es de **Theta(log_M N + s / L)**.

## B. Contrastación con la Tabla D y Búsquedas Independientes

- **Comportamiento en la Tabla D:**
  Al incrementar el ancho de ventana **w** (y por ende la cantidad de registros entregados **s**):
  - Las páginas de nodos internos leídas se mantienen **estrictamente constantes** (siempre h - 1 = 2 páginas).
  - Las páginas de hojas leídas crecen de forma lineal y proporcional a **s / L_ocup**.

- **Comparación contra s búsquedas independientes desde la raíz:**
  Si para obtener los **s** registros del rango se realizaran **s** búsquedas puntuales independientes descendiendo cada vez desde la raíz, el costo total de E/S sería:
  Páginas con búsquedas independientes = s * h = **Theta(s * log_M N)**.

**Conclusión:**
El enlace secuencial directo entre hojas adyacentes (`siguiente`) permite "pagar" el costo de bajada desde la raíz una sola vez (h - 1), transformando el recorrido del resto del rango en una lectura secuencial contigua de hojas. Esto hace que las consultas por rango en el Árbol B+ sean enormemente más eficientes que múltiples búsquedas puntuales.

### Tabla de Referencia

                               TABLA D: BÚSQUEDAS POR RANGO

+--------+-------------------------+------------------------+------------------------+---------------+
| Ancho w| Regs.Entreg. / Consulta | Págs. Leídas / Consult | Hojas Leídas / Consult | µs / Consulta |
+--------+-------------------------+------------------------+------------------------+---------------+
|     10 |                    9,56 |                   3,03 |                   1,03 |         25,92 |
|    100 |                   95,36 |                   3,70 |                   1,70 |         21,06 |
|   1000 |                  956,33 |                  10,44 |                   8,44 |         97,72 |
|  10000 |                 9351,75 |                  75,99 |                  73,99 |        849,07 |

---
# Uso de IA

## Qué hice con IA y qué hice manualmente

Use la ia en dos aspectos , **delegacion de tareas que no requerian mucho esfuerzo** como verificarInvariantes(), estadisticas() , VolcarArbol() y VolcarPagina(),TestArbolBMas , ExperimentoBMas y tambien en el **Analisis de estructura** para hacer una implementacion de la manera mas legible y eficiente posible , un ejemplo de ello fue hacer el metodo de insertar de manera recursiva y con metodos auxiliares .

## Herramienta y Promts

Use mas que nada **Gemini** y un poco de **Claude** y los promts mas relevantes fueron:

- "Ya esta el motodo de abrir corregime o decime si falta algo y asi despues proseguimos con los metodos auxiliares de leerPagina() y escribirPagina() " 

- "para empezar con el desafio de insertar() al ser el metodo mas complicado por los posibles split , quiero ir de a poco para entender bien como funciona , hice una version muy basica que ingresa el par en la hoja pero sin agregar ningun split y colocando el par en la ultima casilla disponible de las listas de la pagina hoja, ahora bien, cual es la mejor estrategia de aca en adelante , hacer que la funcion sea recursiva o la mantenemos asi ? es mejor tener metodos auxiliares de splitHoja y splitInterno para los posibles split o que este todo dentro de insertar() ?"

- "te paso como quedo la clase , la clase exception y la interfaz ya los implemente tambien solo que no te paso , te doy una vez mas la rubrica para que verifiques que todo este bien y cumpla con los requisitos del tp , decime si falta algo o hay algo que corregir y si todo esta bien implementa la clase de prueba y explicame como funciona".#   P r o y e c t o 3 _ A l g o 3 
 
 