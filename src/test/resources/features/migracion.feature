Feature: Generacion de archivo plano de migracion de creditos

  Como ingeniero de migracion
  Quiero generar un archivo plano de ancho fijo a partir de un Excel de estructura
  Para migrar creditos al sistema bancario destino

  Background:
    Given el archivo de estructura "DetalleTecnico.xlsx"

  Scenario: Verificar carga del esquema
    Then el esquema debe tener 294 campos definidos
    And las listas de referencia deben estar cargadas

  Scenario: Generar archivo con 100 registros validos
    When genero un archivo plano con 100 registros
    Then el archivo "salida.txt" debe existir
    And cada linea debe tener la longitud total esperada
    And todos los campos obligatorios deben tener datos validos

  Scenario: Validar reglas cruzadas de tipo de tasa
    When genero un archivo plano con 10 registros
    Then si TIPOTASA-IN es "F" entonces TASDINAM-IN debe ser "X"
    And si TIPOTASA-IN es "F" entonces TIPCUOTA-IN debe ser "F"
    And si TIPOTASA-IN es "D" entonces TIPCUOTA-IN debe ser "V"

  Scenario: Validar saldos calculados
    When genero un archivo plano con 10 registros
    Then VALSLDACT-IN debe ser igual a CAPVIGACT-IN mas CAPVENCI-IN

  Scenario: Validar formato de fechas
    When genero un archivo plano con 10 registros
    Then todos los campos de fecha deben tener formato aaaammdd valido

  Scenario: Validar calculo de capital pagado
    When genero un archivo plano con 10 registros
    Then VALCAPPAG-IN debe ser igual a VALPRESTA-IN menos VALSLDACT-IN con minimo cero

  Scenario: Validar regla NITEMPCON condicional por linea de credito
    When genero un archivo plano con 10 registros
    Then si CODLINCRE-IN es diferente de "131" entonces NITEMPCON-IN debe ser ceros

  Scenario Outline: Generar archivo parametrizado
    When genero un archivo plano con <cantidad> registros
    Then el archivo "salida.txt" debe existir
    And el archivo debe contener exactamente <cantidad> lineas

    Examples:
      | cantidad |
      | 5        |
      | 50       |
      | 200      |
