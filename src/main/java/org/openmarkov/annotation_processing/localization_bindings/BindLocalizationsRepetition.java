package org.openmarkov.annotation_processing.localization_bindings;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows {@link BindLocalizations} to be repeated.
 *
 * @author jrico
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface BindLocalizationsRepetition {
    /**
     * The multiple repetitions of {@link BindLocalizations}.
     *
     * @return multiple repetitions of {@link BindLocalizations}.
     */
    BindLocalizations[] value();
}

