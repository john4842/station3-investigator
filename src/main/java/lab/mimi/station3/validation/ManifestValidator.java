package lab.mimi.station3.validation;

import lab.mimi.station3.domain.VialRecord;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ManifestValidator {
    private ManifestValidator() {}

    public static void requireWellFormed(List<VialRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("manifest must contain at least one vial");
        }
        Set<Integer> seen = new HashSet<>();
        Set<Integer> closedRecipes = new HashSet<>();
        int expectedVial = 1;
        double previousEnd = -1;
        long previousEndFrame = -1;
        VialRecord first = requireRecord(records.getFirst());
        int currentRecipe = first.recipeId();
        int currentRecipeVials = 0;
        for (VialRecord candidate : records) {
            VialRecord record = requireRecord(candidate);
            if (!seen.add(record.vial())) throw new IllegalArgumentException("duplicate vial " + record.vial());
            if (record.vial() != expectedVial) throw new IllegalArgumentException("vials must be contiguous from 1; expected " + expectedVial);
            if (record.startSeconds() <= previousEnd) throw new IllegalArgumentException("time windows overlap or are out of order at vial " + record.vial());
            if (record.startFrame() <= previousEndFrame) throw new IllegalArgumentException("frame windows overlap or are out of order at vial " + record.vial());
            if (record.recipeId() != currentRecipe) {
                if (currentRecipeVials != 3) {
                    throw new IllegalArgumentException("recipe " + currentRecipe + " must contain exactly three contiguous vials");
                }
                closedRecipes.add(currentRecipe);
                if (closedRecipes.contains(record.recipeId())) {
                    throw new IllegalArgumentException("recipe " + record.recipeId() + " recurs after its vial group ended");
                }
                currentRecipe = record.recipeId();
                currentRecipeVials = 0;
            }
            currentRecipeVials++;
            expectedVial++;
            previousEnd = record.endSeconds();
            previousEndFrame = record.endFrame();
        }
        if (currentRecipeVials != 3) {
            throw new IllegalArgumentException("recipe " + currentRecipe + " must contain exactly three contiguous vials");
        }
    }

    private static VialRecord requireRecord(VialRecord record) {
        if (record == null) throw new IllegalArgumentException("manifest cannot contain a null vial record");
        return record;
    }
}

