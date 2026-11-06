package com.treinos.app.ui

import com.treinos.app.data.local.WorkoutPlanWithDetails

/**
 * Plano exibido na Home (H3-15): o selecionado, se ainda existir; senão o
 * primeiro `main` ativo; senão o primeiro da lista. A seleção sobrevive à
 * geração de novos planos e volta ao main quando o selecionado é excluído.
 */
fun resolveSelectedPlan(
    plans: List<WorkoutPlanWithDetails>,
    selectedPlanId: Long?,
): WorkoutPlanWithDetails? =
    plans.firstOrNull { it.plan.id == selectedPlanId }
        ?: plans.firstOrNull { it.plan.kind == "main" }
        ?: plans.firstOrNull()