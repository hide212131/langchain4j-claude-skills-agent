package io.github.hide212131.langchain4j.claude.skills.runtime.context;

import java.util.Collections;
import java.util.List;

/**
 * Placeholder for the progressive disclosure / context packing logic. Will evolve with detailed
 * heuristics in later tasks.
 */
public final class ContextPackingService {

    public List<String> pack(List<String> disclosures) {
        return disclosures == null ? Collections.emptyList() : List.copyOf(disclosures);
    }
}
