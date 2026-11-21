package com.ibm.rnd.alexa.handler;

/**
 * Implemented by handlers that a user can invoke. {@link HelpIntentHandler} collects every
 * {@code HelpTopic} bean, so new capabilities document themselves without touching the help handler.
 */
public interface HelpTopic {

    /** Short plain-text phrase completing "You can ...", for example {@code "order a drink"}. */
    String helpPhrase();
}
