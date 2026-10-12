/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records the player's experiments with the reference device.
 * Only actually observed, valid readings are recorded; unobserved combinations stay empty.
 * At least one combination must be predicted before it is observed, so the last unobserved
 * combination can only be observed by predicting it first if no prediction was made yet.
 */
public final class ExperimentSession {
    private final List<String> inputs;
    private final Reading[] observed;
    private final Long[] predicted;
    private int predictions;
    private int correctPredictions;

    /**
     * Creates a new session.
     *
     * @param inputs the input port ids; combination index bit i (from the left) is input i
     */
    public ExperimentSession(List<String> inputs) {
        this.inputs = inputs;
        int n = 1 << inputs.size();
        observed = new Reading[n];
        predicted = new Long[n];
    }

    /**
     * @return the number of input combinations
     */
    public int getCombinationCount() {
        return observed.length;
    }

    /**
     * Converts a combination index to input values.
     *
     * @param combination the combination index
     * @return the input values by port id
     */
    public Map<String, Boolean> toInputs(int combination) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (int i = 0; i < inputs.size(); i++)
            m.put(inputs.get(i), ((combination >> (inputs.size() - 1 - i)) & 1) != 0);
        return m;
    }

    /**
     * @return the number of observed combinations
     */
    public int getObservedCount() {
        int n = 0;
        for (Reading r : observed)
            if (r != null) n++;
        return n;
    }

    /**
     * @param combination the combination index
     * @return the observed reading or null if not yet observed
     */
    public Reading getObserved(int combination) {
        return observed[combination];
    }

    /**
     * @param combination the combination index
     * @return the prediction made before observing, or null; for several outputs: whether any was predicted lit
     */
    public Boolean getPredicted(int combination) {
        Long p = predicted[combination];
        return p == null ? null : p != 0;
    }

    /**
     * @param combination the combination index
     * @return the predicted value (outputs packed, the first output as the highest bit), or null
     */
    public Long getPrediction(int combination) {
        return predicted[combination];
    }

    /**
     * Returns true if a plain observation (without prediction) is allowed.
     *
     * @param combination the combination index
     * @return true if allowed
     */
    public boolean canObserveWithoutPrediction(int combination) {
        if (observed[combination] != null || predictions > 0)
            return true;
        return getObservedCount() < observed.length - 1;
    }

    /**
     * Returns true if a prediction can be made, i.e. the combination is not yet observed.
     *
     * @param combination the combination index
     * @return true if a prediction is possible
     */
    public boolean canPredict(int combination) {
        return observed[combination] == null;
    }

    /**
     * Records an observation. Invalid readings are not recorded.
     *
     * @param combination the combination index
     * @param reading     the reading of the reference device
     * @return true if recorded
     */
    public boolean observe(int combination, Reading reading) {
        if (!reading.isValid())
            return false;
        if (!canObserveWithoutPrediction(combination))
            return false;
        observed[combination] = reading;
        return true;
    }

    /**
     * Records a prediction for an unobserved combination together with the following observation.
     *
     * @param combination the combination index
     * @param prediction  the predicted output
     * @param reading     the reading of the reference device, obtained after the prediction was fixed
     * @return true if the prediction was correct, false if wrong or not recorded
     */
    public boolean predictAndObserve(int combination, boolean prediction, Reading reading) {
        return predictAndObserve(combination, prediction ? 1L : 0L, reading);
    }

    /**
     * Records a predicted value for an unobserved combination together with the following observation.
     *
     * @param combination the combination index
     * @param prediction  the predicted outputs, packed with the first output as the highest bit
     * @param reading     the reading of the reference device, obtained after the prediction was fixed
     * @return true if the prediction was correct, false if wrong or not recorded
     */
    public boolean predictAndObserve(int combination, long prediction, Reading reading) {
        if (!canPredict(combination) || !reading.isValid())
            return false;
        predicted[combination] = prediction;
        observed[combination] = reading;
        predictions++;
        boolean ok = reading.getValue() == prediction;
        if (ok) correctPredictions++;
        return ok;
    }

    /**
     * @return the number of predictions made
     */
    public int getPredictions() {
        return predictions;
    }

    /**
     * @return the number of correct predictions
     */
    public int getCorrectPredictions() {
        return correctPredictions;
    }

    /**
     * @return true if all combinations are observed and at least one prediction was made
     */
    public boolean isComplete() {
        return getObservedCount() == observed.length && predictions > 0;
    }
}
