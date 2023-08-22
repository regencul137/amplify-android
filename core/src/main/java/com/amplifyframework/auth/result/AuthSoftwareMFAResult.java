/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amplifyframework.auth.result;

import androidx.annotation.NonNull;
import androidx.core.util.ObjectsCompat;

import com.amplifyframework.auth.result.step.AuthNextSoftwareMFAStep;

import java.util.Objects;

/**
 * Wraps the result of a associate software MFA operation.
 */
public final class AuthSoftwareMFAResult {
    private final AuthNextSoftwareMFAStep nextStep;

    /**
     * Wraps the result of a associate software MFA operation.
     *
     * @param nextStep Details about the next step in the associate software MFA process (or whether the flow is now done).
     */
    public AuthSoftwareMFAResult(@NonNull AuthNextSoftwareMFAStep nextStep) {
        this.nextStep = Objects.requireNonNull(nextStep);
    }

    /**
     * Returns details about the next step in the associate software MFA process (or whether the flow is now done).
     *
     * @return details about the next step in the associate software MFA process (or whether the flow is now done)
     */
    @NonNull
    public AuthNextSoftwareMFAStep getNextStep() {
        return nextStep;
    }

    /**
     * When overriding, be sure to include nextStep in the hash.
     *
     * @return Hash code of this object
     */
    @Override
    public int hashCode() {
        return ObjectsCompat.hash(
                getNextStep()
        );
    }

    /**
     * When overriding, be sure to include nextStep in the comparison.
     *
     * @return True if the two objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        } else {
            AuthSoftwareMFAResult authSoftwareMFAResult = (AuthSoftwareMFAResult) obj;
            return ObjectsCompat.equals(getNextStep(), authSoftwareMFAResult.getNextStep());
        }
    }

    /**
     * When overriding, be sure to include nextStep in the output string.
     *
     * @return A string representation of the object
     */
    @NonNull
    @Override
    public String toString() {
        return "AuthSoftwareMFAResult{" +
                "nextStep=" + getNextStep() +
                '}';
    }
}
