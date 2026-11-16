package com.treinos.app.ui

/**
 * H4-5: plate math — quais anilhas colocar por lado para chegar à carga-alvo.
 *
 * Funções puras, sem Android e sem persistência: o inventário é parâmetro
 * (hoje o default de academia; um item futuro pode ligar configuração). A
 * regra é conservadora de propósito — nunca carrega acima do alvo.
 */

/**
 * Inventário de anilhas disponível. `barKg` é a barra; `plateKg` lista as
 * denominações disponíveis **por lado** (anilhas ilimitadas por denominação).
 */
data class PlateInventory(
    val barKg: Double = 20.0,
    val plateKg: List<Double> = listOf(20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
)

/** Padrão de academia: barra de 20 kg + 20/15/10/5/2,5/1,25 kg por lado. */
val DEFAULT_PLATE_INVENTORY: PlateInventory = PlateInventory()

/**
 * Resultado da quebra de anilhas para uma carga-alvo.
 *
 * @property targetKg  o que o usuário digitou.
 * @property loadedKg  `barKg + 2 * soma(perSideKg)`; **nunca** ultrapassa o alvo.
 * @property perSideKg anilhas de UM lado, em ordem decrescente.
 * @property remainderKg `targetKg - loadedKg` (>= 0); `0` = carga exata.
 */
data class PlateResult(
    val targetKg: Double,
    val loadedKg: Double,
    val perSideKg: List<Double>,
    val remainderKg: Double,
)

/**
 * Quebra gulosa do maior para o menor. Devolve `null` quando a carga não é
 * carregável: alvo não finito, <= 0, abaixo da barra, ou barra inválida.
 * Anilhas não finitas ou <= 0 são ignoradas.
 */
fun plateBreakdown(
    targetKg: Double,
    inventory: PlateInventory = DEFAULT_PLATE_INVENTORY,
): PlateResult? {
    if (!targetKg.isFinite() || targetKg <= 0.0) return null
    if (!inventory.barKg.isFinite() || inventory.barKg <= 0.0) return null
    if (targetKg < inventory.barKg) return null
    val plates = inventory.plateKg.filter { it.isFinite() && it > 0.0 }.sortedDescending()
    var remaining = targetKg - inventory.barKg
    val perSide = mutableListOf<Double>()
    for (plate in plates) {
        while (remaining >= 2.0 * plate) {
            perSide.add(plate)
            remaining -= 2.0 * plate
        }
    }
    // Guarda de ponto flutuante: a soma pode divergir por arredondamento do
    // alvo digitado; a regra é nunca carregar acima do alvo.
    var loaded = inventory.barKg + 2.0 * perSide.sum()
    while (loaded > targetKg && perSide.isNotEmpty()) {
        perSide.removeAt(perSide.size - 1)
        loaded = inventory.barKg + 2.0 * perSide.sum()
    }
    return PlateResult(
        targetKg = targetKg,
        loadedKg = loaded,
        perSideKg = perSide.toList(),
        remainderKg = (targetKg - loaded).coerceAtLeast(0.0),
    )
}

/**
 * `true` só para equipamento carregado com anilhas (`barbell`). Halteres,
 * máquina, cabo e peso corporal não mostram o atalho — evita matemática errada.
 */
fun isPlateLoadedEquipment(equipment: String?): Boolean = equipment == "barbell"
