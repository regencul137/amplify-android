package com.amplifyframework.auth.result.step;

import androidx.annotation.NonNull;
import androidx.core.util.ObjectsCompat;

/**
 * This object represents all details around the next step in the associate software MFA process. It holds an instance of the
 * {@link AuthSoftwareMFAStep} enum to denote the step itself and supplements it with additional details which can
 * optionally accompany it. If there is no next step, {@link #getSoftwareMFAStep()} ()} will have a value of DONE.
 */
public class AuthNextSoftwareMFAStep {
    private final AuthSoftwareMFAStep softwareMFAStep;
    private final String verifyCode;

    /**
     * Gives details on the next step, if there is one, in the reset password flow.
     * @param softwareMFAStep the next step in the associate software MFA flow (could be optional or required)
     * @param verifyCode Details about how a code was sent, if relevant to the current step
     */
    public AuthNextSoftwareMFAStep(AuthSoftwareMFAStep softwareMFAStep, String verifyCode) {
        this.softwareMFAStep = softwareMFAStep;
        this.verifyCode = verifyCode;
    }

    /**
     * Returns the next step in the associate software MFA flow (could be optional or required).
     * @return the next step in the associate software MFA flow (could be optional or required)
     */
    public AuthSoftwareMFAStep getSoftwareMFAStep() {
        return softwareMFAStep;
    }

    /**
     * Details about how a code was sent, if relevant to the current step.
     * @return Details about how a code was sent, if relevant to the current step - null otherwise
     */
    public String getVerifyCode() {
        return verifyCode;
    }

    /**
     * When overriding, be sure to include softwareMFAStep and verifyCode in the hash.
     * @return Hash code of this object
     */
    @Override
    public int hashCode() {
        return ObjectsCompat.hash(
                getSoftwareMFAStep(),
                getVerifyCode()
        );
    }

    /**
     * When overriding, be sure to include softwareMFAStep and verifyCode in the comparison.
     * @return True if the two objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        } else {
            AuthNextSoftwareMFAStep authNextSoftwareMFAStep = (AuthNextSoftwareMFAStep) obj;
            return ObjectsCompat.equals(getSoftwareMFAStep(), authNextSoftwareMFAStep.getSoftwareMFAStep()) &&
                    ObjectsCompat.equals(getVerifyCode(), authNextSoftwareMFAStep.getVerifyCode());
        }
    }

    /**
     * When overriding, be sure to include softwareMFAStep and verifyCode in the output
     * string.
     * @return A string representation of the object
     */
    @NonNull
    @Override
    public String toString() {
        return "AuthNextSoftwareMFAStep{" +
                "softwareMFAStep=" + getSoftwareMFAStep() +
                ", verifyCode=" + getVerifyCode() +
                '}';
    }
}
