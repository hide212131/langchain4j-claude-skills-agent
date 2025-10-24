package io.github.hide212131.langchain4j.claude.skills.runtime.guard;

import java.util.Objects;

/**
 * Guard responsible for enforcing invocation-level policies such as allowlists and budgets.
 */
public final class SkillInvocationGuard {

    public void ensureAllowed(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
    }

    public void checkBudgets(BudgetSnapshot budgetSnapshot) {
        Objects.requireNonNull(budgetSnapshot, "budgetSnapshot");
        if (budgetSnapshot.remainingToolCalls() <= 0) {
            throw new IllegalStateException("Tool call budget exhausted");
        }
    }

    public record BudgetSnapshot(int remainingToolCalls, int remainingTokenBudget) {}
}
