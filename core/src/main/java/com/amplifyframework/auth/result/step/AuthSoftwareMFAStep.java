package com.amplifyframework.auth.result.step;

import com.amplifyframework.auth.AuthCategoryBehavior;
import com.amplifyframework.core.Action;
import com.amplifyframework.core.Consumer;

/**
 * Represents the various common steps a user could be in for the associate software MFA flow.
 */
public enum AuthSoftwareMFAStep {
    /**
     * Submit this code from the user Authenticator App using
     * {@link AuthCategoryBehavior#verifySoftwareMFAToken(String, String, Action, Consumer)}
     */
    CONFIRM_ASSOCIATE_MFA_WITH_CODE,
    /**
     * The flow is completed and no further steps are needed.
     */
    DONE;
}
