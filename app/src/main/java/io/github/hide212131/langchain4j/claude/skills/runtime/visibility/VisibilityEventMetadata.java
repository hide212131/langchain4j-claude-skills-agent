package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.time.Instant;

/** runId/skillId/phase/step など共通メタデータを保持する。 */
public record VisibilityEventMetadata(String runId, String skillId, String phase, String step, Instant timestamp) {

    public VisibilityEventMetadata(String runId, String skillId, String phase, String step, Instant timestamp) {
        this.runId = runId;
        this.skillId = skillId;
        this.phase = requirePhase(phase);
        this.step = requireStep(step);
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public VisibilityEventMetadata withSkillId(String newSkillId) {
        return new VisibilityEventMetadata(runId, newSkillId, phase, step, timestamp);
    }

    private String requirePhase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("phase");
        }
        return value;
    }

    private String requireStep(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("step");
        }
        return value;
    }
}
