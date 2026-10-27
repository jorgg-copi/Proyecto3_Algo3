#  Sistema de Indexación en Disco con Árbol B+ (Java)

Un motor de indexación de alto rendimiento basado en **Árboles B+** para la gestión e inspección de grandes volúmenes de datos almacenados en disco. Diseñado para trabajar sobre datasets masivos (como los datasets TSV de IMDb `title.basics.tsv`), el sistema implementa lectura/escritura física en paginación binaria estricta, minimizando las operaciones de E/S (*I/O*) y ofreciendo búsquedas con complejidad temporal `O(log_M N)`.

---

##  ¿Qué es y para qué sirve?

En bases de datos y motores de búsqueda, los archivos de datos masivos no se cargan completamente en la memoria RAM debido a limitaciones físicas. Para consultar un registro de manera eficiente sin realizar un recorrido secuencial exhaustivo (`O(N)`), se utilizan índices en disco.

Este proyecto implementa:
1. **Un Motor de Árbol B+ Persistente en Disco (`indice.dat`):** Organizado en páginas físicas de tamaño fijo (`P` bytes), donde los nodos internos guían la búsqueda binaria y las hojas contienen pares `(clave, posicion_en_disco)` conectados entre sí mediante listas enlazadas para búsquedas por rango ultrarrápidas.
2. **Un Almacenamiento de Datos Variable (`datos.dat`):** Guarda las filas de texto codificadas en UTF-8 con sus metadatos de longitud y clave en un archivo binario independiente.
3. **Una CLI Interactiva (`BMasApp`):** Para construir índices, ejecutar búsquedas por clave o rango, comparar métricas contra escaneo secuencial, validar la integridad del árbol e inspeccionar las páginas físicas en hexadecimal/texto.

---

##  ¿Cómo funciona la arquitectura?

### 1. Estructura de Páginas e Índices

El árbol ajusta dinámicamente sus capacidades por página según el tamaño de página `P` configurado (por defecto 4096 bytes):

* **Capacidad de Nodos Internos (`M`):**
  `M = floor((P - 4) / 12)`
* **Capacidad de Nodos Hoja (`L`):**
  `L = floor((P - 12) / 16)`

#### Diagrama de la Estructura en Disco
```text
                       [ Cabecera (P bytes) ]
                                 |
                                 v
                     [ Nodo Raíz Interno (#1) ]
                        /                \
                       v                  v
             [ Nodo Interno (#2) ]     [ Nodo Interno (#3) ]
                /         \               /         \
               v           v             v           v
           [ Hoja #4 ] -> [ Hoja #5 ] -> [ Hoja #6 ] -> [ Hoja #7 ]
```

### 2. Formato del Archivo de Índice (`indice.dat`)

* **Página 0 (Cabecera):**
  * `Magic Number`: `0x41454433` ("AED3")
  * Versión: `1`
  * Parámetros estructurales: `P`, `M`, `L`
  * Punteros globales: Número de página de la raíz, total de páginas, niveles del árbol, cantidad total de claves.
* **Páginas 1 a `N` (Nodos Internos y Hojas):**
  * **Hojas (Tipo 0):** Guardan la lista de claves `long` (8 bytes) y sus posiciones físicas `long` (8 bytes) en `datos.dat`, además del puntero a la siguiente hoja.
  * **Internos (Tipo 1):** Guardan la lista de claves de separación `long` y los números de página hijo `int` (4 bytes).

### 3. Formato del Archivo de Datos (`datos.dat`)

Cada registro insertado en `datos.dat` sigue la estructura binaria:
```text
[ Clave long (8 B) ] [ Largo int (4 B) ] [ Contenido UTF-8 (Largo B) ]
```

---

##  Comandos y Uso

### Compilación

Para compilar todo el código fuente en el directorio actual:

```bash
javac *.java
```

---

### Comandos de la CLI (`BMasApp`)

#### 1. Construir el Índice (`construir`)
Lee un archivo `.tsv` (como `title.basics.tsv`), escribe los registros binarios en `datos.dat` e inserta sus claves en el `indice.dat`.

```bash
# Uso estándar (300,000 filas por defecto, página de 4096 bytes)
java BMasApp construir title.basics.tsv

# Uso personalizado: 100,000 filas, página de 8192 bytes e inserción aleatoria
java BMasApp construir title.basics.tsv 100000 8192 --aleatorio
```

#### 2. Búsqueda por Clave (`buscar`)
Busca una clave por formato `tt` o numérico directo. Devuelve el camino de páginas recorridas, la cantidad de transferencias a disco (E/S) y el registro completo.

```bash
java BMasApp buscar tt0111161
# o también:
java BMasApp buscar 111161
```
*Salida de ejemplo:*
```text
paginas leidas: 3 (#1 -> #14 ->#208)
pos=104820 tt0111161  short  Carmencita  Carmencita  0  1894  \N  1  Documentary,Short
```

#### 3. Búsqueda por Rango (`rango`)
Aprovecha la cadena secuencial enlazada de las hojas del Árbol B+ para obtener todos los registros dentro del intervalo `[desde, hasta]` sin recorrer nodos internos adicionales.

```bash
java BMasApp rango 111160 111200 5
```

#### 4. Búsqueda Secuencial (`secuencial`)
Realiza un escaneo directo registro por registro sobre `datos.dat`. Permite comparar la eficiencia del índice frente a la fuerza bruta.

```bash
java BMasApp secuencial tt0111161
```

#### 5. Información del Índice (`info`)
Muestra un reporte detallado del estado físico del árbol, porcentaje de ocupación de las páginas y estadísticas del almacenamiento.

```bash
java BMasApp info
```

#### 6. Verificación de Invariantes (`verificar`)
Realiza un diagnóstico del árbol comprobando que se cumplan las propiedades teóricas fundamentales:
* Hojas en el mismo nivel.
* Cotas de claves (*bounded keys* superiores e inferiores).
* Factor de llenado mínimo y máximo.
* Integridad de los punteros enlazados entre hojas.
* Coincidencia entre claves registradas y el contador global.

```bash
java BMasApp verificar
```

#### 7. Volcado de Página (`pagina`)
Inspecciona el contenido interno de una página física concreta por su número.

```bash
java BMasApp pagina 1
```

---

## Rendimiento: Índice B+ vs. Búsqueda Secuencial

| Métrica | Búsqueda Secuencial | Búsqueda por Árbol B+ |
| :--- | :--- | :--- |
| **Complejidad Temporal** | `O(N)` | `O(log_M N)` |
| **Lecturas en Disco (300k reg.)** | ~150,000 - 300,000 registros | **3 a 4 páginas físicas** |
| **Tiempo Promedio** | Dependiente del tamaño de archivo | **< 5 ms** |
| **Consultas por Rango** | Requiere escaneo total (`O(N)`) | Directo vía hojas (`O(log_M N + K)`) |

---

## Estructura del Código Fuente

* **`ArbolBMas.java`**: Lógica central del Árbol B+. Administra paginación, splits de nodos y hojas, balanceo, búsquedas binarias internas, rangos e invariantes.
* **`ArchivoDatos.java`**: Gestor de acceso aleatorio a registros en disco (`datos.dat`).
* **`BMasApp.java`**: Interfaz de línea de comandos (CLI) que conecta los módulos con el usuario final.
* **`ConsumidorEntrada.java`**: Interfaz funcional utilizada para procesar resultados de consultas por rango sin sobrecargar la memoria RAM.

---

##  Autor

* **Jorge Hermosilla**